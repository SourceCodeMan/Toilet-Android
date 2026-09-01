CREATE TABLE IF NOT EXISTS players (
  id         TEXT    PRIMARY KEY,
  name       TEXT    NOT NULL,
  lifetime   INTEGER NOT NULL,
  best_day   INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

-- The board is one query: the top twenty by lifetime, oldest first on a tie.
CREATE INDEX IF NOT EXISTS players_by_lifetime ON players (lifetime DESC, updated_at ASC);
