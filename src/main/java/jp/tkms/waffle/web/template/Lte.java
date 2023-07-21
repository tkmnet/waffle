package jp.tkms.waffle.web.template;

import com.sun.jna.platform.EnumUtils;
import jp.tkms.utils.concurrent.FutureArrayList;
import jp.tkms.utils.stream.Editor;
import jp.tkms.waffle.Main;
import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.util.FileName;
import spark.Request;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static jp.tkms.waffle.web.template.Html.*;

public class Lte {
  public static final String COLLAPSED_STATE_KEY = "collapsed_state_key";

  public enum DivSize {
    F12, F12Md6Sm3, F12Md12Sm6;

    @Override
    public String toString() {
      switch (this) {
        case F12Md6Sm3:
          return "col-md-3 col-sm-6 col-12";
        case F12Md12Sm6:
          return "col-md-6 col-sm-12 col-12";
      }
      return "col-12";
    }
  }

  public enum Color {
    Primary, Secondary, Info, Success, Warning, Danger, Light,
    Outline_Primary, Outline_Secondary, Outline_Info, Outline_Success, Outline_Warning, Outline_Danger, Outline_Light;

    @Override
    public String toString() {
      return this.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
  }

  public static String loading() {
    return div("overlay text-center", fasIcon("2x", "fa-sync-alt fa-spin"));
  }


  public static String divContainerFluid(String... values) {
    return div("container-fluid", values);
  }

  public static String divRow(String... values) {
    return div("row", values);
  }

  public static String divCol(DivSize divSize, String... values) {
    return div(divSize.toString(), values);
  }

  public static String card(String title, String tools, String body, String footer, String additionalClass,
                            String additionalBodyClass) {
    String innerContent = "";
    if (title != null || tools != null) {
      if (tools != null && tools.indexOf("data-card-widget=\"collapse\"") > 0) {
        innerContent = Html.element("div", new Attributes(value("class", "card-header"),
            value("data-card-widget","collapse")),
          h3("card-title", title),
          div("card-tools", tools)
        );
      } else {
        innerContent =
          div("card-header",
            h3("card-title", title),
            div("card-tools", tools)
          );
      }
    }
    innerContent += div(listBySpace("card-body", additionalBodyClass),
      removeNull(body)
    );
    if (footer != null) {
      innerContent += div("card-footer", footer);
    }
    return div(listBySpace("card", additionalClass), innerContent);
  }

  public static String card(String title, String tools, String body, String footer) {
    return card(title, tools, body, footer, null, null);
  }

  public static String collapsedCard(String title, String tools, String body, String footer, String additionalClass,
                                     String additionalBodyClass, String collapseKey, boolean defaultCollapse, Request request) {
    if (title == null) { title = null; }
    if (collapseKey == null) { collapseKey = title; }
    collapseKey = getCollapsedStateKey(collapseKey);
    if (additionalClass == null) { additionalClass = ""; }
    if (tools == null) { tools = ""; }
    boolean collapsed = defaultCollapse;
    if (request.cookies().containsKey(collapseKey)) {
      collapsed = request.cookies().get(collapseKey).equals("1");
    }
    return card(title, tools  + " " + Lte.cardToggleButton(collapsed),
      body, footer , (collapsed ? "collapsed-card " : "")
        + COLLAPSED_STATE_KEY + " " + collapseKey + " " + additionalClass, additionalBodyClass);
  }

  public static String getCollapsedStateKey(String key) {
    return COLLAPSED_STATE_KEY + "__" + FileName.removeRestrictedCharacters(key);
  }

  public static String badge(String colorType, Attributes attributes, String value) {
    return Html.span("badge badge-" + colorType, attributes, value);
  }

  public static String cardToggleButton(boolean flag) {
    return Html.element("button", new Attributes(value("class", "btn btn-tool"),
      value("type", "button"), value("data-card-widget","collapse"),
      value("data-toggle", "tooltip")), (flag?Html.fasIcon("plus"):Html.fasIcon("minus")));
  }

  public static String infoBox(DivSize divSize, String icon, String iconBgCLass, String text, String number) {
    return divCol(divSize, div("info-box",
      span(listBySpace("info-box-icon", iconBgCLass), null, fasIcon(icon)),
      div("info-box-content",
        span("info-box-text", null, text),
        span("info-box-number", null, number)
      )));
  }

  public static String formTextAreaGroup(String name, String label, int rows,
                                      String contents, ArrayList<FormError> errors) {
    String id = "input" + name;
    return div("form-group",
      (label != null ?
        element("label", new Attributes(value("for", id)), label) : null),
      element("textarea",
        new Attributes(
            value("class", "form-control"),
            value("data-auto-resize", String.valueOf(rows)),
            value("name", name),
            value("id", id)
        )
        , Html.sanitaize(contents)
      )
    );
  }

  public static String formTextAreaGroup(String name, String label, String contents, ArrayList<FormError> errors) {
    return formTextAreaGroup(name, label, contents.split("\\n").length, contents, errors);
  }


  public static String formDataEditorGroup(String name, String label, String type,
                                         String contents, String snippetScript, ArrayList<FormError> errors) {
    String id = "input" + name;
    return div("form-group",
      (label != null ?
        element("label", new Attributes(value("for", id)), label) : null),
      element("textarea",
        new Attributes(
          value("class", "form-control"),
          value("data-editor", type),
          value("data-snippet", snippetScript),
          value("name", name),
          value("id", id)
        )
        , contents
      )
    );
  }

  public static String formDataEditorGroup(String name, String label, String type,
                                           String contents, ArrayList<FormError> errors) {
    return formDataEditorGroup(name, label, type, contents, "", errors);
  }

  public static String formJsonEditorGroup(String name, String label, String mode,
                                           String contents, ArrayList<FormError> errors) {
    String id = "input" + name;
    return div("form-group",
      (label != null ?
        element("label", new Attributes(value("for", id)), label) : null),
      element("textarea",
        new Attributes(
          value("class", "form-control"),
          value("data-jsoneditor", mode),
          value("name", name),
          value("id", id)
        )
        , contents
      )
    );
  }

  public static String readonlyTextAreaGroup(String name, String label, int rows, String contents) {
    String id = "input" + name;
    return div("form-group",
      (label != null ?
        element("label", new Attributes(value("for", id)), label) : null),
      element("textarea",
        new Attributes(
          value("class", "form-control"),
          value("rows", String.valueOf(rows)),
          value("name", name),
          value("id", id),
          value("readonly", null)
        )
        , contents
      )
    );
  }

  public static String readonlyTextAreaGroup(String name, String label, String contents) {
    return readonlyTextAreaGroup(name, label, contents.split("\\n").length, contents);
  }

  public static String formInputGroup(String type, String name, String label,
                                      String placeholder, String value, ArrayList<FormError> errors) {
    String id = "input" + name;
    return div("form-group",
      (label != null ?
        element("label", new Attributes(value("for", id)), label) : null),
      attribute("input",
        value("type", type),
        value("class", "form-control"),
        value("name", name),
        value("id", id),
        value("value", (value == null?"":value)),
        value("placeholder", placeholder)
      )
    );
  }

  public static String formSelectGroup(String name, String label, List<String> optionList, ArrayList<FormError> errors) {
    return formSelectGroup(name, label, optionList, null, errors);
  }

  public static String formSelectGroup(String name, String label, List<String> optionList, String selected, ArrayList<FormError> errors) {
    LinkedHashMap<String, String> map = new LinkedHashMap<>();
    for (String value : optionList) {
      map.put(value, null);
    }
    return formSelectGroup(name, label, map, selected, errors);
  }

  public static String formSelectGroup(String name, String label, LinkedHashMap<String, String> optionMap, ArrayList<FormError> errors) {
    return formSelectGroup(name, label, optionMap, null, errors);
  }

  public static String formSelectGroup(String name, String label, LinkedHashMap<String, String> optionMap, String selected, ArrayList<FormError> errors) {
    String id = "input" + name;
    String options = "";
    for (Map.Entry<String, String> option : optionMap.entrySet()) {
      if (selected != null && selected.equals(option.getKey())) {
        if (option.getValue() == null) {
          options += element("option", new Attributes("selected"), option.getKey());
        } else {
          options += element("option", new Html.Attributes(Html.value("value", option.getKey()), "selected"), option.getValue());
        }
      } else {
        if (option.getValue() == null) {
          options += element("option", null, option.getKey());
        } else {
          options += element("option", new Html.Attributes(Html.value("value", option.getKey())), option.getValue());
        }
      }
    }
    return div("form-group",
      (label != null ? element("label", new Attributes(value("for", id)), label) : null),
      element("select", new Attributes(value("name", name), value("id", id), value("class", "form-control")), options)
    );
  }

  public static String formSelect2Group(String name, String label, ArrayList<String> optionList, ArrayList<FormError> errors) {
    String id = "input" + name;
    String options = "";
    for (String option : optionList) {
      options += element("option", null, option);
    }
    return div("form-group",
      (label != null ? element("label", new Attributes(value("for", id)), label) : null),
      element("select", new Attributes(value("id", id),value("style", "height:1.5em;"),
        value("class", "form-control select2")), options),
      Html.javascript("$(document).ready(function(){$('#" + id + "').select2()});")
    );
  }

  public static String formSwitchGroup(String name, String label, boolean state, ArrayList<FormError> errors) {
    String id = "input" + name;
    return div("form-group",
      div("custom-control custom-switch",
        attribute("input",
          (state ? value("checked", "checked") : null),
          value("name", name),
          value("value", name),
          value("id", id),
          value("type", "checkbox"),
          value("class", "custom-control-input")),
        (label == null ? null :
          element("label",
            new Attributes(value("for", id), value("class", "custom-control-label")),
            label)
        )
      )
    );
  }

  public static String formSubmitButton(String color, String value) {
    return element("button",
      new Attributes(value("type", "submit"), value("class", "btn btn-" + color)),
      value);
  }

  public static String disabledTextInput(String name, String label, String value) {
    String id = "input" + name;
    return div("form-group",
      (label != null ? element("label", null, label) : null),
      attribute("input",
        value("id", id),
        value("name", name),
        value("type", "text"),
        value("value", value),
        value("class", "form-control"),
        value("disabled", null)
      )
    );
  }

  public static String readonlyTextInput(String label, String value) {
    return div("form-group",
      (label != null ? element("label", null, label) : null),
      attribute("input",
        value("type", "text"),
        value("value", value),
        value("class", "form-control"),
        value("readonly", null)
      )
    );
  }

  public static String readonlyTextInputWithCopyButton(String label, String value) {
    return readonlyTextInputWithCopyButton(label, value, false);
  }

  public static String readonlyTextInputWithCopyButton(String label, String value, boolean isSmall) {
    UUID uuid = UUID.randomUUID();
    return div("form-group",
      (label != null ? element("label", null, label) : null),
      div("input-group" + (isSmall?" input-group-sm":""),
        attribute("input",
          value("type", "text"),
          value("value", value),
          value("id", uuid.toString()),
          value("class", "form-control"),
          value("readonly", null)
        ),
        span("input-group-append", null,
          element("button", new Attributes(value("class", "btn btn-falt btn-secondary"),
            value("type", "button"),
            value("id", uuid.toString().concat("_")),
            value("onclick", "document.getElementById('" + uuid.toString() + "').select();document.execCommand('copy');toastr.info('Copied');")), farIcon("clipboard"))
        )
      )
    );
  }

  public static String openButton(String label, String value) {
    return span(null, null,
      element("button", new Attributes(value("class", "btn btn-sm btn-outline-secondary"),
        value("type", "button"),
        value("onclick",
          "simplepost('/@system/Open','" + value + "');" +
            "toastr.info('Opened: " + value + "');")), label)
    );
  }

  public static String clipboardButton(String label, String value) {
    UUID uuid = UUID.randomUUID();
    return span(null, null,
      attribute("input",
        value("type", "text"),
        value("value", value),
        value("id", uuid.toString()),
        value("class", "form-control"),
        value("style", "display:none;"),
        value("readonly", null)),
      element("button", new Attributes(value("class", "btn btn-sm btn-outline-secondary"),
        value("type", "button"),
        value("id", uuid.toString().concat("_")),
        value("onclick",
          "var element=document.getElementById('" + uuid.toString() + "');" +
            "element.style.display='inline-block';" +
            "element.select();" +
            "document.execCommand('copy');toastr.info('Copied: ' + element.value);" +
            "element.style.display='none';")), label)
    );
  }

  public static String disabledKnob(String name, String color, double min, double max, double step, boolean isClockwise, double value, String label) {
    String id = "input" + name;
    return div("d-inline-block", div("text-center", attribute("input",
      value("id", id),
      value("name", name),
      value("type", "text"),
      value("value", String.valueOf(value)),
      value("class", "dial"),
      value("data-rotation", isClockwise ? "clockwise" : "anticlockwise"),
      value("data-fgColor", color),
      value("data-min", String.valueOf(min)),
      value("data-max", String.valueOf(max)),
      value("data-step", String.valueOf(step)),
      value("data-readonly", "true")
    ), br(), label));
  }

  public static String table(String classValue, String id, Editor<ArrayList<TableValue>> headersFunc, Editor<FutureArrayList<TableRow>> contentsFunc) {
    ArrayList<TableValue> headers = (headersFunc == null ? null : headersFunc.apply(new ArrayList<>()));
    FutureArrayList<TableRow> contents = (contentsFunc == null ? null : contentsFunc.apply(new FutureArrayList<>(Main.interfaceThreadPool)));
    String headerValue = "";
    StringBuilder stringBuilder = new StringBuilder();
    try {
      if (headers != null) {
        for (TableValue header : headers) {
          headerValue += element("th", new Attributes(value("style", header.style)), header.value);
        }
      }
      for (TableRow row : contents) {
        try {
          StringBuilder rowValue = new StringBuilder();
          for (TableValue value : row) {
            rowValue.append(element("td", new Attributes(value("style", value.style)), value.value));
          }
          stringBuilder.append(element("tr", row.attributes, rowValue.toString()));
        } catch (NullPointerException e) {
          // NOP
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return elementWithClass("table", listBySpace("table", classValue),
      (headers != null ?
        element("thead", null, element("tr", null, headerValue)) : null),
      element("tbody", new Attributes(value("id", id)), stringBuilder.toString())
    );
  }

  public static String table(String classValue, Editor<ArrayList<TableValue>> headersFunc, Editor<FutureArrayList<TableRow>> contentsFunc) {
    return table(classValue, UUID.randomUUID().toString(), headersFunc, contentsFunc);
  }

  public static String table(String classValue, Table table) {
    String headerValue = "";
    if (table.tableHeaders() != null) {
      for (TableValue header : table.tableHeaders()) {
        headerValue += element("th", new Attributes(value("style", header.style)), header.value);
      }
    }
    StringBuilder stringBuilder = new StringBuilder();
    try {
      ArrayList<Future<String>> list = new ArrayList<>();
      for (Future<TableRow> futureRow : table.tableRows()) {
        list.add(Main.interfaceThreadPool.submit(() -> {
          try {
            TableRow row = futureRow.get();
            StringBuilder rowValue = new StringBuilder();
            for (TableValue value : row) {
              rowValue.append(element("td", new Attributes(value("style", value.style)), value.value));
            }
            return element("tr", row.attributes, rowValue.toString());
          } catch (NullPointerException e) {
            return "";
          }
        }));
      }
      for (Future<String> row : list) {
        stringBuilder.append(row.get());
      }
    } catch (InterruptedException | ExecutionException e) {
      ErrorLogMessage.issue(e);
    }

    return elementWithClass("table", listBySpace("table", classValue),
      (table.tableHeaders() != null ?
        element("thead", null, element("tr", null, headerValue)) : null),
      element("tbody", new Attributes(value("id", table.id.toString())), stringBuilder.toString())
    );
  }

  public static String button(String color, String value) {
    return element("button",
      new Attributes(value("class", "btn btn-" + color)),
      value);
  }

  public static String button(String href, Color color, boolean isBlock, String value) {
    return a(href, "btn btn-" + color.toString()
      + (isBlock ? " btn-block" : ""), null, value);
  }

  public static class FormError {

  }

  public static class TableValue {
    String style;
    String value;

    public TableValue(String style, String value) {
      this.style = style;
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }
  }

  public static class TableRow extends ArrayList<TableValue> {
    Html.Attributes attributes = null;

    public TableRow(TableValue... list) {
      super(Arrays.asList(list));
    }

    public TableRow(String... list) {
      for (String value : list) {
        add(new TableValue(null, value));
      }
    }

    public TableRow setAttributes(Attributes attributes) {
      this.attributes = attributes;
      return this;
    }
  }

  public static class Table {
    public String id;
    public Table() {
      this(UUID.randomUUID().toString());
    }
    public Table(String id) {
      this.id = id;
    }
    public ArrayList<TableValue> tableHeaders() { return null; };
    public ArrayList<Future<TableRow>> tableRows() { return null; };
  }

  public static String errorNoticeTextAreaGroup(String contents) {
    return div("form-group",
      element("textarea",
        new Attributes(
          value("class", "form-control  is-invalid"),
          value("style", "font-family:monospace;"),
          value("rows", String.valueOf(contents.split("\\n").length)),
          value("readonly", null)
        )
        , contents
      )
    );
  }

  public static String alert(Color color, String text) {
    return div("alert alert-" + color.toString(), text);
  }
}
