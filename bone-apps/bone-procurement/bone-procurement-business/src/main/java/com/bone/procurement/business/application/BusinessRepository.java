package com.bone.procurement.business.application;

import com.bone.procurement.business.domain.model.business.Award;
import com.bone.procurement.business.domain.model.business.Bid;
import com.bone.procurement.business.domain.model.business.Capa;
import com.bone.procurement.business.domain.model.business.Quotation;
import java.util.List;
import java.util.Optional;

/** 业务聚合读写端口：只由 infrastructure 适配实现，application 不直接持有 SDK 仓储。 */
public interface BusinessRepository {

  Bid saveBid(Bid bid);

  Optional<Bid> findBid(Long tenantId, String rfxNo, String supplierId);

  Optional<Bid> findBidById(Long tenantId, String bidId);

  Optional<Bid> findBidBySourceRef(Long tenantId, String sourceRef);

  List<Bid> findBids(Long tenantId, String rfxNo);

  Quotation saveQuotation(Quotation quotation);

  List<Quotation> findQuotations(Long tenantId, String rfxNo);

  Award saveAward(Award award);

  Optional<Award> findAward(Long tenantId, String rfxNo);

  Capa saveCapa(Capa capa);

  Optional<Capa> findCapa(Long tenantId, String capaNo);

  Capa updateCapa(Capa capa);
}
