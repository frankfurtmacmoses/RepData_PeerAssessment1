package ctagsinterface.main;

import java.util.Map;
import java.util.Set;

import javax.swing.ImageIcon;

public class Tag {

	private final String name;
	private String file;
	private int line;
	private final String pattern;
	private String kind;
	private Map<String, String> extensions;
	private Map<String, String> attachments;
	private static final String LINE_KEY = "line";
	private static final String KIND_KEY = "kind";
	private static final String [] namespaceExtensions = {
		"class", "struct", "union", "interface"
	};

	public Tag(String name, String file, String pattern) {
		this.name = name;
		this.file = file;
		this.pattern = pattern;
	}
	public void setExtensions(Map<String, String> extensions) {
		this.extensions = extensions;
		kind = extensions.containsKey(KIND_KEY) ? extensions.get(KIND_KEY) : "";
		line = extensions.containsKey(LINE_KEY) ? Integer.valueOf(extensions.get(LINE_KEY)) : -1;
	}
	public void setAttachments(Map<String, String> attachments) {
		this.attachments = attachments;
	}
	public String getName() {
		return name;
	}
	public String getFile() {
		return file;
	}
	public String getPattern() {
		return pattern;
	}
	public int getLine() {
		return line;
	}
	public String getKind() {
		return kind;
	}
	public String getExtension(String name) {
		return extensions.get(name);
	}
	public Set<String> getExtensions() {
		return extensions.keySet();
	}
	public Set<String> getAttachments() {
		return attachments.keySet();
	}
	public String getAttachment(String name) {
		return attachments.get(name);
	}
	public ImageIcon getIcon() {
		return CtagsInterfacePlugin.getIcon(this);
	}
	public String getNamespace() {
		for (int i = 0; i < namespaceExtensions.length; i++) {
			String ext = getExtension(namespaceExtensions[i]);
			if (ext != null)
				return ext;
		}
		return null;
	}
	public String getQualifiedName() {
		String ns = getNamespace();
		if (ns == null)
			return getName();
		return "(" + ns + ") " + getName();
	}
	public void setFile(String file) {
		this.file = file;
	}
}
