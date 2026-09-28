package com.expensebook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FxmlLoadTest {

    @Test
    @DisplayName("Verify all 14 FXML resource files exist and contain no invalid Double.MAX_VALUE references")
    public void testFxmlFilesValid() throws Exception {
        List<String> fxmls = List.of(
                "/fxml/onboarding.fxml",
                "/fxml/login.fxml",
                "/fxml/main_layout.fxml",
                "/fxml/dashboard.fxml",
                "/fxml/expense_modal.fxml",
                "/fxml/transactions.fxml",
                "/fxml/calendar.fxml",
                "/fxml/analytics.fxml",
                "/fxml/budget.fxml",
                "/fxml/income.fxml",
                "/fxml/recurring.fxml",
                "/fxml/reports.fxml",
                "/fxml/settings.fxml",
                "/fxml/admin.fxml"
        );

        for (String fxml : fxmls) {
            try (InputStream is = getClass().getResourceAsStream(fxml)) {
                assertNotNull(is, "FXML file must exist on classpath: " + fxml);
                String content = new String(is.readAllBytes());
                assertFalse(content.contains("Double.MAX_VALUE"),
                        "FXML must not contain Java expression Double.MAX_VALUE: " + fxml);
                assertTrue(content.contains("fx:controller"), "FXML must specify a controller: " + fxml);
            }
        }
    }
}
