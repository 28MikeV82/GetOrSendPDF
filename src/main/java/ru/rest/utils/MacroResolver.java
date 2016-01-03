package ru.rest.utils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MacroResolver {
    /**
     * The method resolves masked string. Mask contain few macros with syntax like
     *   %param1|param2|'default'% and each param is resolved by name within passed <b>params</b>.
     *   For example "%grz|vin|'unknown'%.pdf" mask is resolved to "grz-value.pdf" if 'grz' is
     *   specified or "vin-value.pdf" if 'grz' is not specified but 'vin' is specified or
     *   "unknown.pdf" if 'grz' and 'vin' are not specified.
     * @param mask
     * @param params
     * @return masked string with resolved macros
     */
    static public String resolve(String mask, Map<String, String> params) {
        String result = mask;
        Matcher matcher = Pattern.compile("(%.+?%)").matcher(mask);
        while (matcher.find()) {
            String macro = matcher.group();
            String trimmedMacro = macro.substring(1, macro.length() - 1);
            String replacement = "";
            for (String param: trimmedMacro.split("\\|")) {
                if (params.containsKey(param) && !"null".equals(params.get(param))) {
                    replacement = params.get(param);
                    break;
                }
                if (param.startsWith("'")) {
                    replacement = param.substring(1, param.length() - 1);
                    break;
                }
            }
            result = result.replace(macro, replacement);
        }
        return result;
    }
}