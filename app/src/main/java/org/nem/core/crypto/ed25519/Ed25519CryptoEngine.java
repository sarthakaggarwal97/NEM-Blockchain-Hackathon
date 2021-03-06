package org.nem.core.crypto.ed25519;

import org.nem.core.crypto.BlockCipher;
import org.nem.core.crypto.CryptoEngine;
import org.nem.core.crypto.Curve;
import org.nem.core.crypto.DsaSigner;
import org.nem.core.crypto.KeyAnalyzer;
import org.nem.core.crypto.KeyGenerator;
import org.nem.core.crypto.KeyPair;

/**
 * Class that wraps the Ed25519 specific implementation.
 */
public class Ed25519CryptoEngine implements CryptoEngine {

	@Override
	public Curve getCurve() {
		return Ed25519Curve.ed25519();
	}

	@Override
	public DsaSigner createDsaSigner(final KeyPair keyPair) {
		return new Ed25519DsaSigner(keyPair);
	}

	@Override
	public KeyGenerator createKeyGenerator() {
		return new Ed25519KeyGenerator();
	}

	@Override
	public BlockCipher createBlockCipher(final KeyPair senderKeyPair, final KeyPair recipientKeyPair) {
		return new Ed25519BlockCipher(senderKeyPair, recipientKeyPair);
	}

	@Override
	public KeyAnalyzer createKeyAnalyzer() {
		return new Ed25519KeyAnalyzer();
	}
}
