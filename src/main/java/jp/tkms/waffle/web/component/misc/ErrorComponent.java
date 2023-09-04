package jp.tkms.waffle.web.component.misc;

import jp.tkms.waffle.web.component.AbstractAccessControlledComponent;
import jp.tkms.waffle.web.component.ResponseBuilder;
import jp.tkms.waffle.web.template.Html;
import jp.tkms.waffle.web.template.Link;
import jp.tkms.waffle.web.template.MainTemplate;
import spark.Spark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class ErrorComponent extends AbstractAccessControlledComponent {
  static public void register() {
    Spark.notFound(new ResponseBuilder(() -> new ErrorComponent()));
    Spark.internalServerError(new ResponseBuilder(() -> new ErrorComponent()));
  }

  @Override
  public void controller() {
    logger.info(response.status() + ": " + request.url());

    if (response.status() == 404 && request.url().indexOf("/bm/") > 0) {
      response.redirect(request.url(), 302);
      return;
    }

    new MainTemplate() {
      @Override
      protected ArrayList<Map.Entry<String, String>> pageNavigation() {
        return null;
      }

      @Override
      protected String pageTitle() {
        return "" + response.status();
      }

      @Override
      protected ArrayList<Link> pageBreadcrumb() {
        return new ArrayList<>(Arrays.asList(Link.entry("" + response.status())));
      }

      @Override
      protected String pageContent() {
        return Html.h1("text-center", Html.fasIcon("question"));
      }
    }.render(this);
  }
}
