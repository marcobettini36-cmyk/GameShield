import unittest
from update_lists import normalize, parse

class FeedTests(unittest.TestCase):
    def test_normalizes(self):
        self.assertEqual(normalize("*.CASINO.com."), "casino.com")
        self.assertEqual(normalize("bücher.de"), "xn--bcher-kva.de")
    def test_deduplicates(self):
        self.assertEqual(parse("# source\ncasino.com\nCASINO.com\n"), {"casino.com"})
    def test_rejects_invalid_data(self):
        for invalid in ("<html>failure</html>", "1.1.1.1", "https://casino.com", "# empty", "-bad.com"):
            with self.assertRaises(ValueError):
                parse(invalid)
    def test_suffix_is_not_substring(self):
        self.assertNotEqual(normalize("notcasino.com"), normalize("casino.com"))

if __name__ == "__main__":
    unittest.main()
