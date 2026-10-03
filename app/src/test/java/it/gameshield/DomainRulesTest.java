package it.gameshield;
import org.junit.Test;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
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
    @Test public void bundledFeedLoadsAndBlocksRealSources() throws Exception {
        Set<String> domains = new HashSet<>();
        try (Reader reader = new InputStreamReader(new FileInputStream(new File(System.getProperty("gameshield.assets"), "gambling.txt")), StandardCharsets.UTF_8)) { DomainRules.readInto(reader, domains); }
        assertTrue(domains.size() > 10000); DomainRules rules = new DomainRules(domains);
        assertTrue(rules.blocks("bet365.it")); assertTrue(rules.blocks("new.mirror.stake.com")); assertTrue(rules.blocks("sunbet.it")); assertFalse(rules.blocks("example.org"));
        for (String host : Arrays.asList("google.com", "wikipedia.org", "github.com", "dns.google", "cloudflare-dns.com", "dns.quad9.net")) assertFalse(host, rules.blocks(host));
        for (String host : Arrays.asList("playzilla.com", "excitewin.com", "new.playzilla.com")) assertTrue(host, rules.blocks(host));
    }
    @Test(expected = IOException.class) public void streamingParserRejectsCorruptFeed() throws Exception { DomainRules.readInto(new StringReader("casino.com\n<html>error</html>"), new HashSet<>()); }
}
