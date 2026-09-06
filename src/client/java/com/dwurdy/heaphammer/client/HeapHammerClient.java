package com.dwurdy.heaphammer.client;

import net.fabricmc.api.ClientModInitializer;
import com.dwurdy.heaphammer.HeapHammer;

public class HeapHammerClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HeapHammer.LOGGER.info("Initializing HeapHammer Client...");
	}
}
