package com.chainwatch.backend.collector.client;

import java.io.IOException;

public interface BlockClient {
    long getLatestBlockNumber() throws IOException;

    CollectedBlock getBlock(long blockNumber) throws IOException;
}
