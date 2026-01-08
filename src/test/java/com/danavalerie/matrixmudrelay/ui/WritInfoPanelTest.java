package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.StoreInventoryTracker;
import com.danavalerie.matrixmudrelay.core.WritTracker;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WritInfoPanelTest {
    @Test
    void listThenBuyUsesStoreIdForWritItem() throws Exception {
        String writText = """
                You read the official employment writ: Written in carefully printed text:
                You are required to deliver:
                [ ] a khopesh to the shopkeeper at the clothing shop on Ettercap Street
                [ ] a blue crystal necklace to the young lady at the Souvenir Shop on Prouts
                [ ] two knives to Mardi at Masqueparade on Phedre Road
                [ ] a yellow raincoat to Jenny Tawdry at Tawdry Things on the Maudlin Bridge
                You have until Fri Jan  9 01:03:34 2026 [Bangkok] to complete this job.
                """;
        String inventoryText = """
                The following items are for sale:
                A: a morning star for A$3.46 (one left).
                B: an ash spear for A$2.18 (one left).
                C: a short sword for A$1.68 (three left).
                D: a long sword for A$2.64 (three left).
                E: a knobbly mace for A$1.93 (one left).
                F: a knife for 23p (four left).
                G: a dagger for 45p (three left).
                H: a cutlad for A$2.42 (two left).
                I: a cutlass for A$2.27 (two left).
                J: a bastard sword for A$4.40 (two left).
                K: a large axe for A$3.07 (one left).
                """;

        WritTracker writTracker = new WritTracker();
        writTracker.ingest(writText);
        List<WritTracker.WritRequirement> requirements = writTracker.getRequirements();
        int knifeIndex = -1;
        for (int i = 0; i < requirements.size(); i++) {
            if ("knives".equals(requirements.get(i).item())) {
                knifeIndex = i;
                break;
            }
        }
        assertTrue(knifeIndex >= 0, "Expected to find a writ for knives");

        StoreInventoryTracker storeInventoryTracker = new StoreInventoryTracker();
        List<String> commands = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        WritInfoPanel panel = new WritInfoPanel(commands::add, storeInventoryTracker, errors::add);
        panel.updateWrit(requirements);
        SwingUtilities.invokeAndWait(() -> {
            // wait for updateWrit to populate requirements
        });

        Method handleLink = WritInfoPanel.class.getDeclaredMethod("handleLink", String.class);
        handleLink.setAccessible(true);
        handleLink.invoke(panel, "list:" + knifeIndex);

        storeInventoryTracker.ingest(inventoryText);

        handleLink.invoke(panel, "buy:" + knifeIndex);

        assertEquals(List.of("list", "buy F"), commands);
        assertTrue(errors.isEmpty(), "Expected no errors while buying the knife");
    }
}
