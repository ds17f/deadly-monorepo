import Foundation
import GRDB
import Testing
@testable import deadly

/// Guards against schema drift between the prebuilt catalog seed contract
/// (`data/catalog_schema.json`) and the GRDB schema the app actually ships.
///
/// The seed (`catalog.db`) is copied column-by-column into the live DB by
/// `SeedImportService`. If a live catalog column is renamed/removed without
/// updating the contract (or vice-versa), that copy would fail at runtime on a
/// user's first launch. This test catches it instead by introspecting a freshly
/// migrated in-memory database and asserting every column the contract declares
/// exists, with matching primary keys.
@Suite("Catalog seed schema drift")
struct CatalogSeedSchemaDriftTests {

    private let catalogTables = ["shows", "recordings", "dead_collections", "data_version"]

    @Test("GRDB schema covers the catalog seed contract")
    func grdbSchemaCoversContract() throws {
        let contract = try loadContract()
        let db = try AppDatabase.makeEmpty()

        for table in catalogTables {
            let spec = try #require(contract[table], "contract missing table '\(table)'")

            let (actualCols, actualPk) = try db.read { database -> (Set<String>, [String]) in
                let rows = try Row.fetchAll(database, sql: "PRAGMA table_info(\(table))")
                let cols = Set(rows.compactMap { $0["name"] as String? })
                // pk column: 0 = not part of PK, otherwise 1-based position within the PK.
                let pk = rows
                    .compactMap { row -> (String, Int)? in
                        guard let name = row["name"] as String?,
                              let pos = row["pk"] as Int?, pos > 0 else { return nil }
                        return (name, pos)
                    }
                    .sorted { $0.1 < $1.1 }
                    .map { $0.0 }
                return (cols, pk)
            }

            #expect(!actualCols.isEmpty, "live DB is missing catalog table '\(table)'")

            let missing = spec.columns.filter { !actualCols.contains($0) }
            #expect(
                missing.isEmpty,
                "Catalog seed columns absent from live '\(table)': \(missing) (live has: \(actualCols.sorted()))"
            )
            #expect(
                spec.primaryKey == actualPk,
                "Primary key mismatch for '\(table)': contract=\(spec.primaryKey) live=\(actualPk)"
            )
        }
    }

    // MARK: - Contract loading

    private struct TableSpec {
        let columns: [String]
        let primaryKey: [String]
    }

    private func loadContract() throws -> [String: TableSpec] {
        let url = try #require(Self.findContractFile(), "could not locate data/catalog_schema.json")
        let data = try Data(contentsOf: url)
        let json = try #require(
            try JSONSerialization.jsonObject(with: data) as? [String: Any],
            "catalog_schema.json is not a JSON object"
        )
        let tables = try #require(json["tables"] as? [String: Any], "missing 'tables'")

        var result: [String: TableSpec] = [:]
        for (name, value) in tables {
            guard let table = value as? [String: Any] else { continue }
            let columns = (table["columns"] as? [[String: Any]])?.compactMap { $0["name"] as? String } ?? []
            let pk = (table["primaryKey"] as? [String]) ?? []
            result[name] = TableSpec(columns: columns, primaryKey: pk)
        }
        return result
    }

    /// Walk up from this source file to the repo root to find the contract.
    private static func findContractFile(file: String = #filePath) -> URL? {
        var dir = URL(fileURLWithPath: file).deletingLastPathComponent()
        let fm = FileManager.default
        for _ in 0..<12 {
            let candidate = dir.appendingPathComponent("data/catalog_schema.json")
            if fm.fileExists(atPath: candidate.path) { return candidate }
            dir = dir.deletingLastPathComponent()
        }
        return nil
    }
}
