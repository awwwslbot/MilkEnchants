package com.windsor.meowsEnchants.config;

import com.windsor.meowsEnchants.schema.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 一次加载（启动或 {@code /enchants reload}）的结果。最后一行固定是
 * {@code MEOWSENCHANTS_RELOAD {json}}（契约 §9），编辑器经 RCON 解析它。
 */
public final class ReloadReport {
    public static final String JSON_PREFIX = "MEOWSENCHANTS_RELOAD ";

    /** reason：changed（字段变了）、added（新文件，未注册）、removed（文件消失，保留旧配置）。 */
    public record RestartItem(String enchant, List<String> fields, String reason) {}

    private final TreeSet<String> applied = new TreeSet<>();
    private final List<RestartItem> restartRequired = new ArrayList<>();
    private final List<ConfigValidator.Issue> warnings = new ArrayList<>();
    private final List<ConfigValidator.Issue> errors = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();

    public void applied(String id) { applied.add(id); }
    public void restart(String enchant, List<String> fields, String reason) {
        restartRequired.add(new RestartItem(enchant, List.copyOf(fields), reason));
    }
    public void issue(ConfigValidator.Issue issue) {
        (issue.error() ? errors : warnings).add(issue);
    }
    public void error(String enchant, String path, String message) {
        errors.add(new ConfigValidator.Issue(enchant, path, message, true));
    }
    public void note(String note) { notes.add(note); }

    public List<String> applied() { return List.copyOf(applied); }
    public List<RestartItem> restartRequired() { return List.copyOf(restartRequired); }
    public List<ConfigValidator.Issue> warnings() { return List.copyOf(warnings); }
    public List<ConfigValidator.Issue> errors() { return List.copyOf(errors); }
    public List<String> notes() { return List.copyOf(notes); }
    public boolean ok() { return errors.isEmpty(); }

    public Map<String, Object> toJson(String pluginVersion) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("ok", ok());
        json.put("pluginVersion", pluginVersion);
        json.put("applied", List.copyOf(applied));
        List<Object> restart = new ArrayList<>();
        for (RestartItem item : sorted(restartRequired)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("enchant", item.enchant());
            entry.put("fields", item.fields());
            entry.put("reason", item.reason());
            restart.add(entry);
        }
        json.put("restartRequired", restart);
        json.put("warnings", issues(warnings));
        json.put("errors", issues(errors));
        return json;
    }

    /** 单行 JSON，带前缀。 */
    public String jsonLine(String pluginVersion) {
        return JSON_PREFIX + Json.compact(toJson(pluginVersion));
    }

    /** 给命令发送者看的中文说明，最后一行是 JSON。 */
    public List<String> lines(String pluginVersion, boolean initial) {
        List<String> lines = new ArrayList<>();
        lines.add("[MeowsEnchants] " + (initial ? "加载" : "重载") + "完成：生效 " + applied.size() + " 项，需重启 "
                + restartRequired.size() + " 项，警告 " + warnings.size() + " 条，错误 " + errors.size() + " 条");
        if (!applied.isEmpty()) lines.add("已生效：" + String.join("、", applied));
        for (RestartItem item : sorted(restartRequired)) {
            String what = switch (item.reason()) {
                case "added" -> "新附魔文件，重启后才注册";
                case "removed" -> "附魔文件已删除，保留旧配置直到重启";
                default -> String.join("、", item.fields()) + " 改了，重启后生效";
            };
            lines.add("需重启：" + item.enchant() + "（" + what + "）");
        }
        lines.addAll(notes);
        for (ConfigValidator.Issue issue : errors) lines.add("错误：" + describe(issue));
        for (ConfigValidator.Issue issue : warnings) lines.add("警告：" + describe(issue));
        lines.add(jsonLine(pluginVersion));
        return lines;
    }

    private static String describe(ConfigValidator.Issue issue) {
        return issue.enchant() + (issue.path() == null || issue.path().isEmpty() ? "" : " " + issue.path())
                + "：" + issue.message();
    }

    private static List<RestartItem> sorted(List<RestartItem> items) {
        List<RestartItem> copy = new ArrayList<>(items);
        copy.sort(Comparator.comparing(RestartItem::enchant));
        return copy;
    }

    private static List<Object> issues(List<ConfigValidator.Issue> issues) {
        List<Object> list = new ArrayList<>(issues.size());
        for (ConfigValidator.Issue issue : issues) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("enchant", issue.enchant());
            entry.put("path", issue.path());
            entry.put("message", issue.message());
            list.add(entry);
        }
        return list;
    }
}
