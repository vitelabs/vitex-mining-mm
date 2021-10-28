-- 建dex_order_mining_v2库
CREATE DATABASE `dex_order_mining_v2` /*!40100 DEFAULT CHARACTER SET utf8mb4 */;

use dex_order_mining_v2;

CREATE TABLE `address_estimate_reward` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `cycle_key` bigint unsigned NOT NULL COMMENT '周期',
  `address` varchar(55) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'address',
  `order_mining_total` decimal(58,18) NOT NULL DEFAULT '0.000000000000000000' COMMENT '预估挖的VX总额',
  `vite_market_reward` decimal(58,18) NOT NULL DEFAULT '0.000000000000000000' COMMENT '预估vite市场挖的VX数量',
  `eth_market_reward` decimal(58,18) NOT NULL DEFAULT '0.000000000000000000' COMMENT '预估eth市场挖的VX数量',
  `btc_market_reward` decimal(58,18) NOT NULL DEFAULT '0.000000000000000000' COMMENT '预估btc市场挖的VX数量',
  `usdt_market_reward` decimal(58,18) NOT NULL DEFAULT '0.000000000000000000' COMMENT '预估usdt市场挖的VX数量',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_address_cyclekey` (`address`,`cycle_key`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `mining_address_reward` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `cycle_key` bigint unsigned NOT NULL COMMENT '确认周期',
  `address` varchar(55) NOT NULL COMMENT 'address',
  `order_mining_amount` decimal(58,18) NOT NULL DEFAULT '0.000000000000000000' COMMENT 'VX占比',
  `order_mining_percent` decimal(58,18) NOT NULL DEFAULT '0.000000000000000000' COMMENT 'VX占比',
  `invite_mining_amount` decimal(58,18) NOT NULL,
  `invite_mining_percent` decimal(58,18) NOT NULL,
  `total_reward` decimal(58,18) NOT NULL,
  `data_page` int NOT NULL COMMENT '页码',
  `settle_status` tinyint unsigned NOT NULL COMMENT '0:未发放,1:发放',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uindex_address_cyclekey` (`address`,`cycle_key`),
  KEY `index_cyclekey_datapage_status` (`cycle_key`,`data_page`,`settle_status`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `order_mining_market_reward` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `cycle_key` bigint unsigned NOT NULL COMMENT '确认周期',
  `quote_token_type` tinyint DEFAULT '0' COMMENT '计价币种类型',
  `address` varchar(55) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'address',
  `factor_ratio` decimal(50,18) NOT NULL DEFAULT '0.000000000000000000' COMMENT '计算因子占比',
  `amount` decimal(58,18) NOT NULL DEFAULT '0.000000000000000000' COMMENT 'VX占比',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uindex_address_cyclekey_quote` (`address`,`cycle_key`,`quote_token_type`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `settle_page` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `cycle_key` bigint unsigned NOT NULL COMMENT '确认周期',
  `data_page` int NOT NULL COMMENT '页码',
  `amount` decimal(58,18) NOT NULL DEFAULT '0.000000000000000000' COMMENT 'VX占比',
  `settle_status` tinyint unsigned NOT NULL COMMENT '0:未发放,1:发放中，2：已发放，3：确认完成',
  `block_hash` varchar(45) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `cyclekey_record` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `cycle_key` bigint unsigned NOT NULL COMMENT '确认周期',
  `ctime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `utime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_cyclekey` (`cycle_key`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
