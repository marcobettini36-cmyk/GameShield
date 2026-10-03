package it.gameshield;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class DomainRulesTest {
    @Test public void blocksExactAndNewSubdomains() { DomainRules rules = new DomainRules(Arrays.asList("bet365.it")); assertTrue(rules.blocks("bet365.it")); assertTrue(rules.blocks("NEW.mirror.bet365.it.")); }
    @Test public void doesNotBlockLookalikes() { DomainRules r = new DomainRules(Arrays.asList("casino.com")); assertFalse(r.blocks("notcasino.com")); assertFalse(r.blocks("casino.com.example.org")); }
    @Test public void normalizesUnicodeAndWildcard() { assertEquals("xn--bcher-kva.de", DomainRules.normalize("*.BÜCHER.de.")); }
    @Test public void rejectsUrlsAndIpAddresses() { assertNull(DomainRules.normalize("https://casino.com/path")); assertNull(DomainRules.normalize("1.1.1.1")); assertNull(DomainRules.normalize("com")); assertNull(DomainRules.normalize("a..com")); }
    @Test public void parsesCommentsAndDeduplicates() { assertEquals(1, new DomainRules(DomainRules.parse("# test\ncasino.com # source\nCASINO.com\n")).size()); }
    @Test(expected = IllegalArgumentException.class) public void rejectsCorruptFeed() { DomainRules.parse("<html>error</html>"); }
    @Test(expected = IllegalArgumentException.class) public void rejectsEmptyFeed() { DomainRules.parse("# no rules\n"); }
    @Test public void listIsSnapshot() { List<String> input = new ArrayList<>(); input.add("casino.com"); DomainRules r = new DomainRules(input); input.clear(); assertTrue(r.blocks("casino.com")); }
}
