package ctagsinterface.main;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.regex.Pattern;

import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.IOUtilities;
import org.gjt.sp.util.Log;

public class Parser
{
	public static final String MESSAGE = CtagsInterfacePlugin.MESSAGE;
	public static final String PARSING = MESSAGE + "parsing";
	private static final Pattern TAB_PATTERN = Pattern.compile("\t");
	private static final Pattern COLON_PATTERN = Pattern.compile(":");
	private final Logger logger;
	String tagFileDir;
	HashMap<String, String> sourcePathMap;

	interface TagHandler
	{
		void processTag(Tag t);
	}

	public Parser(Logger logger)
	{
		this.logger = logger;
	}

	private static int countLines(String tagFile) throws IOException
	{
		BufferedReader reader = null;
		try
		{
			reader = new BufferedReader(new FileReader(tagFile));
			int nLines = 0;
			while (reader.readLine() != null)
				nLines++;
			return nLines;
		}
		finally
		{
			IOUtilities.closeQuietly((Closeable) reader);
		}
	}

	void parseTagFile(String tagFile, TagHandler handler)
	{
		if (tagFile == null || tagFile.isEmpty())
			return;
		tagFileDir = new File(tagFile).getAbsoluteFile().getParent();
		if (logger != null)
			logger.beginTask(jEdit.getProperty(PARSING));
		CtagsInterfacePlugin.getIndex().startActivity();
		BufferedReader in = null;
		try
		{
			// First, check the number of lines in the output to provide progress
			if (logger != null)
			{
				int nLines = countLines(tagFile);
				logger.setProgressParams(0, nLines);
			}
			in = new BufferedReader(new FileReader(tagFile));
			String line;
			int parsed = 0;
			while ((line = in.readLine()) != null)
			{
				Tag t = parse(line);
				if (t == null)
					continue;
				handler.processTag(t);
				parsed++;
				if (logger != null)
					logger.setProgress(parsed);
			}
		}
		catch (IOException e)
		{
			Log.log(Log.ERROR, this, e);
		}
		finally
		{
			IOUtilities.closeQuietly((Closeable) in);
		}
		CtagsInterfacePlugin.getIndex().endActivity();
		if (logger != null)
			logger.endTask();
	}

	public void setSourcePathMapping(HashMap<String, String> map)
	{
		sourcePathMap = map;
	}

	private Tag parse(String line)
	{
		Hashtable<String, String> info =
			new Hashtable<String, String>();
		if (line.endsWith("\n") || line.endsWith("\r"))
			line = line.substring(0, line.length() - 1);
		// Find the end of the pattern (pattern may include "\t")
		int idx = line.lastIndexOf(";\"\t");
		if (idx < 0)
			return null;
		// Fixed fields (tag, file, pattern/line number)
		String[] fields = TAB_PATTERN.split(line.substring(0, idx), 3);
		if (fields.length < 3)
			return null;
		String file = fields[1];
		if (! new File(file).isAbsolute())
			file = tagFileDir + '/' + fields[1];
		if (sourcePathMap != null)
		{
			String target = sourcePathMap.get(file);
			if (target != null)
				file = target;
		}
		Tag t = new Tag(fields[0], file, fields[2]);
		// Extensions
		fields = TAB_PATTERN.split(line.substring(idx + 3));
		for (int i = 0; i < fields.length; i++)
		{
			String[] pair = COLON_PATTERN.split(fields[i], 2);
			if (pair.length == 1)	// e.g. file:
				info.put(pair[0], "");
			else if (pair.length == 2)
				info.put(pair[0], pair[1]);
		}
		t.setExtensions(info);
		return t;
	}
}
