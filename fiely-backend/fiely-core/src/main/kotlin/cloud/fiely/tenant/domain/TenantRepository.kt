package cloud.fiely.tenant.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TenantRepository : JpaRepository<TenantEntity, UUID>
