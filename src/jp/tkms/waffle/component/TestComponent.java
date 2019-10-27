package jp.tkms.waffle.component;

import jp.tkms.waffle.component.template.Lte;
import jp.tkms.waffle.component.template.MainTemplate;

import java.util.ArrayList;
import java.util.Arrays;

public class TestComponent extends AbstractComponent {
    @Override
    public void controller() {
        new MainTemplate() {
            @Override
            protected String pageTitle() {
                return "Test";
            }

            @Override
            protected ArrayList<String> pageBreadcrumb() {
                return new ArrayList<String>(Arrays.asList(new String[]{"Test"}));
            }

            @Override
            protected String pageContent() {
                return Lte.sampleCard();
            }
        }.render(this);
    }
}
