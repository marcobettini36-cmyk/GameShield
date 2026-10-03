import unittest
from update_lists import normalize, parse, authorized_cells

class FeedTests(unittest.TestCase):
    def test_normalizes(self):
        self.assertEqual(normalize("*.CASINO.com."), "casino.com")
        self.assertEqual(normalize("bücher.de"), "xn--bcher-kva.de")
        self.assertEqual(normalize("761.xn--p1ai"), "761.xn--p1ai")
    def test_deduplicates(self):
        self.assertEqual(parse("# source\ncasino.com\nCASINO.com\n"), {"casino.com"})
    def test_rejects_invalid_data(self):
        for invalid in ("<html>failure</html>", "1.1.1.1", "https://casino.com", "# empty", "-bad.com"):
            with self.assertRaises(ValueError):
                parse(invalid)
    def test_suffix_is_not_substring(self):
        self.assertNotEqual(normalize("notcasino.com"), normalize("casino.com"))
    def test_authorized_parser_only_reads_website_column(self):
        content = '<td headers="h2">unrelated.example</td><td headers="h5">www.casino.com<br>bet.it</td>'
        self.assertEqual(authorized_cells(content), {"casino.com", "bet.it"})

if __name__ == "__main__":
    unittest.main()
