"""Test partitioned Ion writes on a Trino 484 server with the patched Hive plugin.

Usage: python3 test_partitioned_write.py [SERVER_URL]
Requires a writable catalog named hive.
"""

import json
import sys
from urllib.request import Request, urlopen
from uuid import uuid4


def query(server: str, sql: str) -> list:
    headers = {
        "X-Trino-User": "ion-regression",
        # Keep the VALUES page together until Hive splits it by partition.
        "X-Trino-Session": (
            "redistribute_writes=false,use_preferred_write_partitioning=false,"
            "scale_writers=false,task_scale_writers_enabled=false,task_max_writer_count=1"
        ),
    }
    request = Request(server.rstrip("/") + "/v1/statement", sql.encode(), headers)
    rows = []
    while True:
        with urlopen(request, timeout=30) as response:
            result = json.load(response)
        if "error" in result:
            raise RuntimeError(result["error"]["message"])
        rows.extend(result.get("data", []))
        if "nextUri" not in result:
            return rows
        request = Request(result["nextUri"], headers=headers)


def check_partitioned_write(server: str) -> None:
    schema = f"hive.ion_regression_{uuid4().hex}"
    table = f"{schema}.nested_ion"
    query(server, f"CREATE SCHEMA {schema}")
    try:
        query(server, f"""
            CREATE TABLE {table} (xs array(integer), rec row(n integer), part varchar)
            WITH (format = 'ION', partitioned_by = ARRAY['part'])
            """)
        query(server, f"""
            INSERT INTO {table} VALUES
                (ARRAY[1, 2], CAST(ROW(11) AS ROW(n integer)), 'a'),
                (ARRAY[3, 4], CAST(ROW(22) AS ROW(n integer)), 'b'),
                (ARRAY[5, 6], CAST(ROW(33) AS ROW(n integer)), 'a'),
                (ARRAY[7, 8], CAST(ROW(44) AS ROW(n integer)), 'b')
            """)
        actual = query(server, f"SELECT part, xs, rec.n FROM {table} ORDER BY part, rec.n")
        expected = [["a", [1, 2], 11], ["a", [5, 6], 33], ["b", [3, 4], 22], ["b", [7, 8], 44]]
        if actual != expected:
            raise AssertionError(f"Expected {expected}, got {actual}")
    finally:
        query(server, f"DROP TABLE IF EXISTS {table}")
        query(server, f"DROP SCHEMA {schema}")


if __name__ == "__main__":
    check_partitioned_write(sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080")
    print("Partitioned Ion write and read-back passed")
