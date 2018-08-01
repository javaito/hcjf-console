package org.hcjf.console.shell;

import org.hcjf.properties.SystemProperties;
import org.hcjf.utils.Strings;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author javaito
 */
public class Command {

    private final String line;
    private String command;
    private List<Object> parameters;

    public Command(String line, DateFormat dateFormat) {
        this.line = line;
        List<String> richTexts = Strings.groupRichText(line.trim());
        String newLine = richTexts.get(richTexts.size() - 1);
        String[] parts = newLine.split(Strings.WHITE_SPACE);
        command = parts[0];
        parameters = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            String trimmedPart = parts[i].trim();
            if (trimmedPart.equals("true")) {
                parameters.add(Boolean.TRUE);
            } else if (trimmedPart.equals("false")) {
                parameters.add(Boolean.FALSE);
            } else if (trimmedPart.equals("null")) {
                parameters.add(null);
            } else if (trimmedPart.startsWith(Strings.RICH_TEXT_SEPARATOR)) {
                trimmedPart = trimmedPart.substring(1, trimmedPart.length() - 1);
                trimmedPart = richTexts.get(Integer.parseInt(trimmedPart.replace(Strings.REPLACEABLE_RICH_TEXT, Strings.EMPTY_STRING)));
                trimmedPart = trimmedPart.replace(
                        Strings.RICH_TEXT_SKIP_CHARACTER + Strings.RICH_TEXT_SEPARATOR,
                        Strings.RICH_TEXT_SEPARATOR);
                try {
                    parameters.add(dateFormat.parse(trimmedPart));
                } catch (Exception ex) {
                    parameters.add(trimmedPart);
                }
            } else if (trimmedPart.matches(SystemProperties.get(SystemProperties.HCJF_UUID_REGEX))) {
                parameters.add(UUID.fromString(trimmedPart));
            } else if (trimmedPart.matches(SystemProperties.get(SystemProperties.HCJF_INTEGER_NUMBER_REGEX))) {
                parameters.add(Long.parseLong(trimmedPart));
            } else if (trimmedPart.matches(SystemProperties.get(SystemProperties.HCJF_DECIMAL_NUMBER_REGEX))) {
                parameters.add(Double.parseDouble(trimmedPart));
            } else {
                parameters.add(trimmedPart);
            }
        }
    }

    public String getLine() {
        return line;
    }

    public String getCommand() {
        return command;
    }

    public List<Object> getParameters() {
        return parameters;
    }
}
