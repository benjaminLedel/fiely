package cloud.fiely.tenant.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

/**
 * A tenant owns users, files, and per-tenant configuration. All data in Fiely
 * is scoped to a tenant; a default tenant is seeded by migration V3 for
 * single-tenant deployments and integration tests.
 */
@Entity
@Table(name = "tenants")
class TenantEntity(
    @Id
    val id: UUID,

    @Column(nullable = false, unique = true, length = 64)
    val slug: String,

    @Column(nullable = false, length = 255)
    val name: String,

    /** Per-tenant cap on a single file upload, in bytes. Enforced by FileService. */
    @Column(name = "max_upload_bytes", nullable = false)
    val maxUploadBytes: Long = 100L * 1024 * 1024,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),
) {
    companion object {
        /** Stable UUID of the default tenant seeded by V3__tenants.sql. */
        val DEFAULT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }
}
