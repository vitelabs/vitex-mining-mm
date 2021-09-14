package org.vite.dex.mm.mapper;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.vite.dex.mm.entity.AddressMarketReward;

@Repository
public interface AddressMarketRewardRepository extends JpaRepository<AddressMarketReward, Long> {

}
