package jp.tkms.waffle.web.template;

public class Link {
  String key;
  String target;
  public Link(String target, String key) {
    this.target = target;
    this.key = key;
  }

  public String getKey() {
    return key;
  }

  public String getTarget() {
    if (target == null) {
      return "#";
    }
    return target;
  }

  public String toHtml() {
    return Html.a(getTarget(), getKey());
  }

  public static Link entry(String target, String key) {
    return new Link(target, key);
  }

  public static Link entry(String key) {
    return new Link(null, key);
  }
}
