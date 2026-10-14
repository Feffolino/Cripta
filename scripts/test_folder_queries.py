"""Exercise the production Room SQL against SQLite, including migration from v14.

Run with: python3 -m unittest discover -s scripts -p 'test_*.py'
No Android runtime or extra Python packages are needed.
"""
import pathlib
import re
import sqlite3
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
DB_DIR = ROOT / "app/src/main/kotlin/com/cripta/app/data/db"
DAO = (DB_DIR / "Daos.kt").read_text()


def query_for(method):
    queries = re.findall(r'@Query\((""".*?"""|"[^"\n]*")\)\s+(?:suspend\s+)?fun\s+(\w+)', DAO, re.S)
    return next(q.strip('"') for q, name in queries if name == method)


class FolderQueriesTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        self.db.executescript("""
            CREATE TABLE folders (id INTEGER PRIMARY KEY, name TEXT NOT NULL,
                parentId INTEGER, createdAt INTEGER NOT NULL,
                color INTEGER, emoji TEXT, deletedAt INTEGER);
            CREATE TABLE files (id TEXT PRIMARY KEY, folderId INTEGER, deletedAt INTEGER,
                importedAt INTEGER NOT NULL DEFAULT 0);
            INSERT INTO folders VALUES (1, 'Zulu', NULL, 42, 123, 'x', NULL);
            INSERT INTO folders VALUES (2, 'Alpha', NULL, 43, NULL, NULL, NULL);
            INSERT INTO folders VALUES (3, 'Child', 1, 44, NULL, NULL, NULL);
            INSERT INTO folders VALUES (4, 'Grandchild', 3, 45, NULL, NULL, NULL);
            INSERT INTO files (id, folderId, deletedAt) VALUES ('root', NULL, NULL), ('parent', 1, NULL),
                ('child', 3, NULL), ('grandchild', 4, NULL), ('other', 2, NULL),
                ('trashed', 2, 100);
        """)
        source = (DB_DIR / "CriptaDatabase.kt").read_text()
        migration = source.split("private val MIGRATION_14_15 =", 1)[1].split("/** Delete", 1)[0]
        for sql in re.findall(r'db.execSQL\("([^"]+)"\)', migration):
            self.db.execute(sql)

    def eligible(self):
        return {row[0] for row in self.db.execute(query_for("randomEligibleIds"))}

    def test_migration_preserves_existing_folder_and_initial_defaults(self):
        self.assertEqual(self.db.execute("SELECT * FROM folders WHERE id=1").fetchone(),
                         (1, "Zulu", None, 42, 123, "x", None, 0, 0, None, 0))

    def test_favorites_always_first_and_removal_restores_name_order(self):
        params = {"id": 1, "favorite": True}
        self.db.execute(query_for("setFavorite"), params)
        self.assertEqual([r[0] for r in self.db.execute(query_for("childrenOf"), {"parentId": None})], [1, 2])
        self.db.execute(query_for("setFavorite"), {"id": 1, "favorite": False})
        self.assertEqual([r[0] for r in self.db.execute(query_for("childrenOf"), {"parentId": None})], [2, 1])

    def test_exclusion_is_recursive_and_root_files_remain_eligible(self):
        self.assertEqual(self.eligible(), {"root", "parent", "child", "grandchild", "other"})
        self.db.execute(query_for("setRandomExcluded"), {"id": 1, "excluded": True})
        self.assertEqual(self.eligible(), {"root", "other"})
        self.db.execute(query_for("setRandomExcluded"), {"id": 1, "excluded": False})
        self.assertIn("grandchild", self.eligible())

    def test_nested_exclusion_survives_reincluding_parent(self):
        for folder in (1, 3):
            self.db.execute(query_for("setRandomExcluded"), {"id": folder, "excluded": True})
        self.db.execute(query_for("setRandomExcluded"), {"id": 1, "excluded": False})
        self.assertEqual(self.eligible(), {"root", "parent", "other"})

    def test_trashed_folder_and_descendants_are_not_random_candidates(self):
        self.db.execute("UPDATE folders SET deletedAt=100 WHERE id=1")
        self.assertEqual(self.eligible(), {"root", "other"})

    def test_local_filter_source_excludes_other_folders_and_descendants(self):
        sql = query_for("inFolderWithTags")
        self.assertEqual({r[0] for r in self.db.execute(sql, {"folderId": None})}, {"root"})
        self.assertEqual({r[0] for r in self.db.execute(sql, {"folderId": 1})}, {"parent"})

    def test_folder_preview_contains_direct_files_only(self):
        sql = query_for("latestInFolder")
        self.assertEqual({r[0] for r in self.db.execute(sql, {"folderId": 1, "limit": 4})}, {"parent"})
        self.db.execute("DELETE FROM files WHERE id='parent'")
        # The child and grandchild still have files; the parent must use its folder icon.
        self.assertEqual(list(self.db.execute(sql, {"folderId": 1, "limit": 4})), [])

    def test_sort_is_saved_per_folder_without_reverting_other_properties(self):
        self.db.execute(query_for("setFavorite"), {"id": 1, "favorite": True})
        self.db.execute(query_for("setSort"), {"id": 1, "key": "NAME", "ascending": True})
        self.db.execute(query_for("setSort"), {"id": 2, "key": "SIZE", "ascending": False})
        self.assertEqual(self.db.execute("SELECT sortKey, sortAscending, isFavorite FROM folders WHERE id=1").fetchone(), ("NAME", 1, 1))
        self.assertEqual(self.db.execute("SELECT sortKey, sortAscending FROM folders WHERE id=2").fetchone(), ("SIZE", 0))


if __name__ == "__main__":
    unittest.main()
