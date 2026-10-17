package com.beer.brew;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrewRulesTest {

    private static final long MIN = 60_000;

    private static Dose find(List<Dose> doses, Effect effect) {
        return doses.stream().filter(d -> d.effect() == effect).findFirst().orElseThrow();
    }

    private static boolean has(List<Dose> doses, Effect effect) {
        return doses.stream().anyMatch(d -> d.effect() == effect);
    }

    @Test
    void grainDecidesTheColour() {
        assertEquals(Colour.WHEAT, Colour.of(4, 0));
        assertEquals(Colour.WHEAT, Colour.of(2, 0));
        assertEquals(Colour.PALE, Colour.of(1, 0));
        assertEquals(Colour.PALE, Colour.of(0, 0));
        assertEquals(Colour.AMBER, Colour.of(3, 1));
        assertEquals(Colour.DARK, Colour.of(2, 2));
        assertEquals(Colour.DARK, Colour.of(0, 4));
    }

    @Test
    void temperatureDecidesAleOrLager() {
        Wort pale = new Wort(0, 4, 0, 1, null);
        assertEquals(Style.PALE_ALE, BrewRules.pour(pale, false, false, Wood.PALE, 2).style());
        assertEquals(Style.PILSNER, BrewRules.pour(pale, true, false, Wood.PALE, 2).style());
        assertEquals(Style.MARZEN, Style.of(Colour.AMBER, true));
        assertEquals(Style.STOUT, Style.of(Colour.DARK, false));
        assertEquals("dark_clear", Style.DARK_LAGER.look());
        assertEquals("straw", Style.WHEAT_BEER.look());
    }

    @Test
    void namesPutHoppyAndTheExtraInFront() {
        assertEquals("Hoppy Chocolate Stout", BrewRules.name(new Beer(Style.STOUT, true, Extra.COCOA, null)));
        assertEquals("Strong Pilsner", BrewRules.name(new Beer(Style.PILSNER, false, Extra.SUGAR, null)));
        assertEquals("Berry Wheat Beer", BrewRules.name(new Beer(Style.WHEAT_BEER, false, Extra.BERRIES, null)));
        assertEquals("IPA", BrewRules.name(new Beer(Style.PALE_ALE, true, null, null)));
        assertEquals("Honey IPA", BrewRules.name(new Beer(Style.PALE_ALE, true, Extra.HONEY, null)));
        assertEquals("Hoppy Pilsner", BrewRules.name(new Beer(Style.PILSNER, true, null, null)));
        assertEquals("Märzen", BrewRules.name(Beer.plain(Style.MARZEN)));
    }

    @Test
    void starsComeFromWaitingAndWood() {
        long aged = 60 * MIN;
        long longAged = 180 * MIN;
        assertEquals(2, BrewRules.stars(20 * MIN, aged, longAged, Wood.DARK, Colour.PALE));
        assertEquals(3, BrewRules.stars(20 * MIN, aged, longAged, Wood.PALE, Colour.PALE));
        assertEquals(3, BrewRules.stars(aged, aged, longAged, Wood.PALE, Colour.DARK));
        assertEquals(4, BrewRules.stars(longAged, aged, longAged, Wood.PALE, Colour.DARK));
        assertEquals(5, BrewRules.stars(longAged * 10, aged, longAged, Wood.DARK, Colour.DARK));
        assertEquals(Wood.PALE, Colour.WHEAT.wood());
        assertEquals(Wood.DARK, Colour.AMBER.wood());
    }

    @Test
    void legendsNeedTheirExactBrew() {
        Wort minersWort = new Wort(0, 2, 2, 2, Extra.COCOA);
        assertEquals(Legend.MINERS_STOUT, BrewRules.pour(minersWort, false, false, Wood.DARK, 2).legend());
        assertNull(BrewRules.pour(minersWort, false, false, Wood.PALE, 2).legend(), "needs a dark barrel");
        assertNull(BrewRules.pour(minersWort, true, false, Wood.DARK, 2).legend(), "a dark lager is no stout");

        Wort fest = new Wort(0, 3, 1, 1, Extra.PUMPKIN);
        assertEquals(Legend.FESTBIER, BrewRules.pour(fest, true, false, Wood.DARK, 5).legend());
        assertNull(BrewRules.pour(fest, true, false, Wood.DARK, 4).legend(), "needs 5 stars");

        Wort pils = new Wort(0, 4, 0, 2, null);
        assertEquals(Legend.GLACIER_PILS, BrewRules.pour(pils, true, true, Wood.PALE, 2).legend());
        assertNull(BrewRules.pour(pils, true, false, Wood.PALE, 2).legend(), "needs blue ice");

        Wort honeyWheat = new Wort(4, 0, 0, 1, Extra.HONEY);
        Beer knees = BrewRules.pour(honeyWheat, false, false, Wood.PALE, 3);
        assertEquals(Legend.BEES_KNEES, knees.legend());
        assertEquals("Bee's Knees", BrewRules.name(knees));
        assertNull(BrewRules.pour(honeyWheat, false, false, Wood.DARK, 2).legend(), "needs a pale barrel");
        assertNull(BrewRules.pour(honeyWheat, true, false, Wood.PALE, 3).legend(), "Crystal Wheat is not a Wheat Beer");
    }

    @Test
    void baseEffectsAndExtras() {
        List<Dose> ipa = BrewRules.doses(new Beer(Style.PALE_ALE, true, Extra.HONEY, null), 2, 1.0);
        assertEquals(Effect.HASTE, ipa.getFirst().effect());
        assertEquals(120 * 20, find(ipa, Effect.HASTE).ticks());
        assertEquals(0, find(ipa, Effect.HASTE).amplifier());
        assertTrue(has(ipa, Effect.STRENGTH));
        assertEquals(30 * 20, find(ipa, Effect.REGENERATION).ticks(), "Regeneration lasts a quarter");

        List<Dose> strong = BrewRules.doses(new Beer(Style.STOUT, false, Extra.SUGAR, null), 5, 1.0);
        assertEquals(1, find(strong, Effect.RESISTANCE).amplifier());
        assertEquals(Math.round(210 * 0.75 * 20), find(strong, Effect.RESISTANCE).ticks());
        assertEquals(2, BrewRules.tipsy(new Beer(Style.STOUT, false, Extra.SUGAR, null)));
        assertEquals(1, BrewRules.tipsy(Beer.plain(Style.STOUT)));
    }

    @Test
    void lagersLastLongerAndCheersHelps() {
        int ale = find(BrewRules.doses(Beer.plain(Style.RED_ALE), 3, 1.0), Effect.ABSORPTION).ticks();
        int lager = find(BrewRules.doses(Beer.plain(Style.MARZEN), 3, 1.0), Effect.ABSORPTION).ticks();
        int cheers = find(BrewRules.doses(Beer.plain(Style.RED_ALE), 3, BrewRules.CHEERS), Effect.ABSORPTION).ticks();
        assertEquals(Math.round(150 * 0.5 * 20), ale);
        assertEquals(Math.round(ale * 1.5), lager);
        assertEquals(Math.round(ale * 1.25), cheers);
    }

    @Test
    void legendBonusesApply() {
        Beer miners = BrewRules.pour(new Wort(0, 2, 2, 2, Extra.COCOA), false, false, Wood.DARK, 3);
        List<Dose> doses = BrewRules.doses(miners, 3, 1.0);
        assertEquals(1, find(doses, Effect.HASTE).amplifier());
        assertTrue(has(doses, Effect.RESISTANCE) && has(doses, Effect.STRENGTH) && has(doses, Effect.NIGHT_VISION));

        Beer glacier = BrewRules.pour(new Wort(0, 4, 0, 2, null), true, true, Wood.PALE, 3);
        assertEquals(1, find(BrewRules.doses(glacier, 3, 1.0), Effect.SPEED).amplifier());
    }

    @Test
    void beesKneesHopsHigher() {
        Beer knees = BrewRules.pour(new Wort(4, 0, 0, 1, Extra.HONEY), false, false, Wood.PALE, 3);
        List<Dose> doses = BrewRules.doses(knees, 3, 1.0);
        assertEquals(1, find(doses, Effect.JUMP_BOOST).amplifier());
        assertTrue(has(doses, Effect.SPEED) && has(doses, Effect.REGENERATION));
        assertEquals(3, doses.size(), "each effect once");
    }

    @Test
    void wortAndBeerSurviveStorage() {
        Wort wort = new Wort(1, 2, 1, 2, Extra.COCOA);
        assertEquals(wort, Wort.decode(wort.encode()));
        Wort plain = new Wort(0, 4, 0, 1, null);
        assertEquals(plain, Wort.decode(plain.encode()));
        assertNull(Wort.decode("nonsense"));
        Beer beer = BrewRules.pour(wort, true, false, Wood.DARK, 4);
        assertEquals(beer, Beer.decode(beer.encode()));
        Beer legend = BrewRules.pour(new Wort(0, 4, 0, 2, null), true, true, Wood.PALE, 3);
        assertEquals(legend, Beer.decode(legend.encode()));
        assertNull(Beer.decode("what|0||"));
    }

    @Test
    void kettleBoilsOnlyWhenFull() {
        assertTrue(BrewRules.canBoil(true, 4, 1));
        assertFalse(BrewRules.canBoil(false, 4, 2));
        assertFalse(BrewRules.canBoil(true, 3, 2));
        assertFalse(BrewRules.canBoil(true, 4, 0));
        assertEquals(40 * MIN, BrewRules.fermentMs(true, 20 * MIN, 40 * MIN));
    }
}
