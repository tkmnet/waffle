package jp.tkms.waffle.web.template;

import java.util.ArrayList;
import java.util.Arrays;

public class Html {

  public static String element(String tag, Attributes attribute, String... values) {
    String result = (attribute == null
      ? attribute(tag) : attribute(tag, attribute.toArray(new String[attribute.size()])));
    for (String value : values) {
      result += removeNull(value);
    }
    result += "</" + tag + ">\n";
    return result;
  }

  public static String attribute(String tag, String... attributes) {
    String result = '<' + tag;
    for (String value : attributes) {
      result += ' ' + removeNull(value);
    }
    result += ">\n";
    return result;
  }

  public static String value(String name, String value) {
    return name + "=\"" + removeNull(value).replace("\"", "&#34;") + "\"";
  }

  public static String elementWithClass(String tag, String classValue, String... values) {
    Attributes attributes = null;
    if (classValue != null) {
      attributes = new Attributes(value("class", classValue));
    }
    return element(tag, attributes, values);
  }

  public static String html(String... values) {
    return "<!DOCTYPE html>\n" + element("html", null, values);
  }

  public static String head(String... values) {
    return element("head", null, values);
  }

  public static String meta(String... values) {
    return attribute("meta", values);
  }

  public static String link(String rel, String href) {
    return attribute("link", value("rel", rel), value("href", href));
  }

  public static String body(String classValue, String... values) {
    return element("body", new Attributes(value("class", classValue)), values);
  }

  public static String div(String classValue, String... values) {
    return elementWithClass("div", classValue, values);
  }

  public static String divWithId(String id, String... values) {
    return element("div", new Attributes(value("id", id)), values);
  }

  public static String section(String classValue, String... values) {
    return elementWithClass("section", classValue, values);
  }

  public static String h1(String classValue, String... values) {
    return elementWithClass("h1", classValue, values);
  }

  public static String h2(String classValue, String... values) {
    return elementWithClass("h2", classValue, values);
  }

  public static String h3(String classValue, String... values) {
    return elementWithClass("h3", classValue, values);
  }

  public static String h4(String classValue, String... values) {
    return elementWithClass("h4", classValue, values);
  }

  public static String h5(String classValue, String... values) {
    return elementWithClass("h5", classValue, values);
  }

  public static String h6(String classValue, String... values) {
    return elementWithClass("h6", classValue, values);
  }

  public static String a(String href, String classValue, Attributes attribute, String... values) {
    if (attribute == null) {
      attribute = new Attributes();
    }
    attribute.add(value("href", href));
    attribute.add(value("class", classValue));
    return element("a", attribute, values);
  }

  public static String a(String href, String value) {
    return a(href, null, null, value);
  }

  public static String tiIcon(String fa) {
    return elementWithClass("i", "ti ti-" + fa, "");
  }

  public static String farIcon(String fa) {
    return elementWithClass("i", "far fa-" + fa, "");
  }

  public static String fasIcon(String fa) {
    return elementWithClass("i", "fas fa-" + fa, "");
  }

  public static String fasIcon(String fa, String additionalClass) {
    return elementWithClass("i", listBySpace("fas fa-" + fa, additionalClass), "");
  }

  public static String span(String classValue, Attributes attribute, String... values) {
    if (attribute == null) {
      attribute = new Attributes();
    }
    attribute.add(value("class", classValue));
    return element("span", attribute, values);
  }

  public static String spanWithId(String id, String... values) {
    return element("span", new Attributes(value("id", id)), values);
  }

  public static String spanWithName(String name, String... values) {
    return element("span", new Attributes(value("name", name)), values);
  }

  public static String hr() {
    return attribute("hr");
  }

  public static String br() {
    return attribute("br");
  }

  public static String p(String... values) {
    return element("p", null, values);
  }

  public static String form(String action, Method method, String values) {
    return element("form",
      new Attributes(value("action", action),
        value("method", method.name().toLowerCase())
      ),
      values
    );
  }

  public static String inputHidden(String name, String value) {
    return attribute("input",
      value("type", "hidden"),
      value("name", name),
      value("value", value)
    );
  }

  public static String textareaHidden(String name, String value) {
    return element("textarea", new Attributes(value("name", name), value("class", "d-none")), value);
  }

  public static String javascript(String... values) {
    return element("script", new Attributes(value("type", "text/javascript")), values);
  }

  static String removeNull(String string) {
    return (string == null ? "" : string);
  }

  static String listBySpace(String... values) {
    String result = "";
    for (int i = 0; i < values.length; i++) {
      String value = values[i];
      if (value != null) {
        result += (result != "" ? ' ' : "") + value;
      }
    }
    return result;
  }

    public static String sanitaize(String value) {
      return value.replace("\"", "&quot;")
        .replace("'", "&prime;")
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
    }

    protected String content() {
    return "";
  }

  public String render() {
    return content();
  }

  public enum Method {Post, Get}

  public static class Attributes extends ArrayList<String> {
    public Attributes() {
    }

    public Attributes(String... values) {
      this.addAll(Arrays.asList(values));
    }

    public Attributes add(String name, String value) {
      this.add(value(name, value));
      return this;
    }
  }
}
