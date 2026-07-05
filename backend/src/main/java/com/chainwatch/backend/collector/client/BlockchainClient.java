package com.chainwatch.backend.collector.client;

import com.chainwatch.backend.collector.dto.BlockDto;

/**
 * 체인 데이터 조회 추상화. 구현체는 Ethereum RPC(Web3j), Etherscan 등 공급자별로 존재하며,
 * 향후 Bitcoin/BSC/Polygon 등 다른 체인 추가 시 이 인터페이스를 구현한다.
 *
 * <p>일시적 실패는 {@link com.chainwatch.backend.collector.exception.RpcClientException}으로,
 * 설정 오류는 {@link com.chainwatch.backend.collector.exception.CollectorConfigurationException}으로 던진다.
 */
public interface BlockchainClient {

    long fetchLatestBlockNumber();

    BlockDto fetchBlock(long blockNumber);

    String network();
}
