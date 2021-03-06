/*
 *
 *  * Copyright 2014 http://Bither.net
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *    http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.yjt.wallet.core;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.yjt.wallet.core.crypto.ECKey;
import com.yjt.wallet.core.crypto.EncryptedData;
import com.yjt.wallet.core.crypto.TransactionSignature;
import com.yjt.wallet.core.crypto.hd.DeterministicKey;
import com.yjt.wallet.core.crypto.hd.HDKeyDerivation;
import com.yjt.wallet.core.crypto.mnemonic.MnemonicException;
import com.yjt.wallet.core.db.AbstractDb;
import com.yjt.wallet.core.qrcode.QRCodeUtil;
import com.yjt.wallet.core.script.ScriptBuilder;
import com.yjt.wallet.core.utils.PrivateKeyUtil;
import com.yjt.wallet.core.utils.Utils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Created by songchenwen on 15/6/19.
 */
public class HDAccountCold extends AbstractHD {

    private static final Logger log = LoggerFactory.getLogger(HDAccountCold.class);

    public HDAccountCold(byte[] mnemonicSeed, CharSequence password, boolean isFromXRandom)
            throws MnemonicException.MnemonicLengthException {
        this.mnemonicSeed = mnemonicSeed;
        hdSeed = seedFromMnemonic(mnemonicSeed);
        this.isFromXRandom = isFromXRandom;
        DeterministicKey master          = HDKeyDerivation.createMasterPrivateKey(hdSeed);
        EncryptedData    encryptedHDSeed = new EncryptedData(hdSeed, password, isFromXRandom);
        EncryptedData encryptedMnemonicSeed = new EncryptedData(mnemonicSeed, password,
                isFromXRandom);
        ECKey  k       = new ECKey(mnemonicSeed, null);
        String address = k.toAddress();
        k.clearPrivateKey();
        DeterministicKey accountKey = getAccount(master);
        DeterministicKey externalKey = getChainRootKey(accountKey, AbstractHD.PathType
                .EXTERNAL_ROOT_PATH);
        DeterministicKey internalKey = getChainRootKey(accountKey, PathType
                .INTERNAL_ROOT_PATH);
        DeterministicKey key = externalKey.deriveSoftened(0);
        String firstAddress = key.toAddress();
        accountKey.wipe();
        master.wipe();
        wipeHDSeed();
        wipeMnemonicSeed();
        hdSeedId = AbstractDb.hdAccountProvider.addHDAccount(encryptedMnemonicSeed
                        .toEncryptedString(), encryptedHDSeed.toEncryptedString(), firstAddress,
                                                             isFromXRandom, address, externalKey.getPubKeyExtended(), internalKey
                        .getPubKeyExtended());
        externalKey.wipe();
    }

    public HDAccountCold(byte[] mnemonicSeed, CharSequence password) throws MnemonicException
            .MnemonicLengthException {
        this(mnemonicSeed, password, false);
    }

    public HDAccountCold(SecureRandom random, CharSequence password) throws MnemonicException
            .MnemonicLengthException {
        this(randomByteFromSecureRandom(random, 16), password, random.getClass().getCanonicalName
                ().indexOf("XRandom") >= 0);
    }

    public HDAccountCold(EncryptedData encryptedMnemonicSeed, CharSequence password) throws
            MnemonicException.MnemonicLengthException {
        this(encryptedMnemonicSeed.decrypt(password), password, encryptedMnemonicSeed.isXRandom());
    }

    public HDAccountCold(int hdSeedId) {
        this.hdSeedId = hdSeedId;
        this.isFromXRandom = AbstractDb.hdAccountProvider.hdAccountIsXRandom(hdSeedId);
    }

    public List<byte[]> signHashHexes(final Collection<String> hashes, Collection<PathTypeIndex>
            paths, CharSequence password) throws MnemonicException
            .MnemonicLengthException {
        return signHashes(Collections2.transform(hashes, new Function<String, byte[]>() {
            @Nullable
            @Override
            public byte[] apply(String input) {
                return Utils.hexStringToByteArray(input);
            }
        }), paths, password);
    }

    public List<byte[]> signHashes(Collection<byte[]> hashes, Collection<PathTypeIndex> paths,
                                   CharSequence password) throws MnemonicException
            .MnemonicLengthException {
        assert hashes.size() == paths.size();
        ArrayList<byte[]> sigs = new ArrayList<byte[]>();
        DeterministicKey master = masterKey(password);
        DeterministicKey account = getAccount(master);
        DeterministicKey external = getChainRootKey(account, PathType.EXTERNAL_ROOT_PATH);
        DeterministicKey internal = getChainRootKey(account, PathType.INTERNAL_ROOT_PATH);
        master.wipe();
        account.wipe();
        Iterator<byte[]> hashIterator = hashes.iterator();
        Iterator<PathTypeIndex> pathIterator = paths.iterator();
        while (hashIterator.hasNext() && pathIterator.hasNext()) {
            byte[] hash = hashIterator.next();
            PathTypeIndex path = pathIterator.next();
            DeterministicKey key;
            if (path.pathType == PathType.EXTERNAL_ROOT_PATH) {
                key = external.deriveSoftened(path.index);
            } else {
                key = internal.deriveSoftened(path.index);
            }
            TransactionSignature sig = new TransactionSignature(key.sign(hash),
                                                                TransactionSignature.SigHash.ALL, false);
            sigs.add(ScriptBuilder.createInputScript(sig, key).getProgram());
            key.wipe();
        }
        external.wipe();
        internal.wipe();
        return sigs;
    }

    public String getFirstAddressFromDb() {
        return AbstractDb.hdAccountProvider.getHDFirstAddress(hdSeedId);
    }

    public boolean checkWithPassword(CharSequence password) {
        try {
            decryptHDSeed(password);
            decryptMnemonicSeed(password);
            byte[] hdCopy = Arrays.copyOf(hdSeed, hdSeed.length);
            boolean hdSeedSafe = Utils.compareString(getFirstAddressFromDb(),
                    getFirstAddressFromSeed(null));
            boolean mnemonicSeedSafe = Arrays.equals(seedFromMnemonic(mnemonicSeed), hdCopy);
            Utils.wipeBytes(hdCopy);
            wipeHDSeed();
            wipeMnemonicSeed();
            return hdSeedSafe && mnemonicSeedSafe;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    protected String getEncryptedHDSeed() {
        return AbstractDb.hdAccountProvider.getHDAccountEncryptSeed(hdSeedId);
    }

    @Override
    protected String getEncryptedMnemonicSeed() {
        return AbstractDb.hdAccountProvider.getHDAccountEncryptMnemonicSeed(hdSeedId);
    }

    public String getFullEncryptPrivKey() {
        String encryptPrivKey = getEncryptedMnemonicSeed();
        return PrivateKeyUtil.getFullencryptHDMKeyChain(isFromXRandom, encryptPrivKey);
    }

    public String getQRCodeFullEncryptPrivKey() {
        return QRCodeUtil.HD_QR_CODE_FLAG + getFullEncryptPrivKey();
    }

    private static byte[] randomByteFromSecureRandom(SecureRandom random, int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    public byte[] accountPubExtended(CharSequence password) throws MnemonicException
            .MnemonicLengthException {
        DeterministicKey master = masterKey(password);
        DeterministicKey account = getAccount(master);
        byte[] extended = account.getPubKeyExtended();
        master.wipe();
        account.wipe();
        return extended;
    }

    public String accountPubExtendedString(CharSequence password) throws MnemonicException
            .MnemonicLengthException {
        byte[] extended = accountPubExtended(password);
        String result = "";
        if (isFromXRandom) {
            result += QRCodeUtil.XRANDOM_FLAG;
        }
        result += Utils.bytesToHexString(extended).toUpperCase();
        return result;
    }

    public String xPubB58(CharSequence password) throws MnemonicException
            .MnemonicLengthException {
        DeterministicKey master = masterKey(password);
        DeterministicKey purpose = master.deriveHardened(44);
        DeterministicKey coinType = purpose.deriveHardened(0);
        DeterministicKey account = coinType.deriveHardened(0);
        String xpub = account.serializePubB58();
        master.wipe();
        purpose.wipe();
        coinType.wipe();
        account.wipe();
        return xpub;
    }
}
