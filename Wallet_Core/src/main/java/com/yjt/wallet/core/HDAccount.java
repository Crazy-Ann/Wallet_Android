/*
 * Copyright 2014 http://Bither.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yjt.wallet.core;

import com.yjt.wallet.core.contant.AbstractApp;
import com.yjt.wallet.core.crypto.ECKey;
import com.yjt.wallet.core.crypto.EncryptedData;
import com.yjt.wallet.core.crypto.KeyCrypterException;
import com.yjt.wallet.core.crypto.TransactionSignature;
import com.yjt.wallet.core.crypto.hd.DeterministicKey;
import com.yjt.wallet.core.crypto.hd.HDKeyDerivation;
import com.yjt.wallet.core.crypto.mnemonic.MnemonicCode;
import com.yjt.wallet.core.crypto.mnemonic.MnemonicException;
import com.yjt.wallet.core.db.AbstractDb;
import com.yjt.wallet.core.exception.AddressFormatException;
import com.yjt.wallet.core.exception.PasswordException;
import com.yjt.wallet.core.exception.TxBuilderException;
import com.yjt.wallet.core.qrcode.QRCodeUtil;
import com.yjt.wallet.core.script.ScriptBuilder;
import com.yjt.wallet.core.utils.PrivateKeyUtil;
import com.yjt.wallet.core.utils.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HDAccount extends Address {
    public static final String HDAccountPlaceHolder = "HDAccount";
    public static final String HDAccountMonitoredPlaceHolder = "HDAccountMonitored";
    public static final int MaxUnusedNewAddressCount = 20;

    public interface HDAccountGenerationDelegate {
        void onHDAccountGenerationProgress(double progress);
    }

    private static final double GenerationPreStartProgress = 0.01;

    private static final int LOOK_AHEAD_SIZE = 100;

    private long balance = 0;

    protected transient byte[] mnemonicSeed;
    protected transient byte[] hdSeed;
    protected int hdSeedId = -1;
    protected boolean isFromXRandom;
    private boolean hasSeed;

    private static final Logger log = LoggerFactory.getLogger(HDAccount.class);

    public HDAccount(byte[] mnemonicSeed, CharSequence password) throws MnemonicException
            .MnemonicLengthException {
        this(mnemonicSeed, password, true);
    }

    public HDAccount(byte[] mnemonicSeed, CharSequence password, boolean isSyncedComplete) throws
            MnemonicException
                    .MnemonicLengthException {
        super();
        this.mnemonicSeed = mnemonicSeed;
        hdSeed = seedFromMnemonic(mnemonicSeed);
        DeterministicKey master = HDKeyDerivation.createMasterPrivateKey(hdSeed);
        EncryptedData encryptedHDSeed = new EncryptedData(hdSeed, password, isFromXRandom);
        EncryptedData encryptedMnemonicSeed = new EncryptedData(mnemonicSeed, password,
                                                                isFromXRandom);
        DeterministicKey account = getAccount(master);
        account.clearPrivateKey();
        initHDAccount(account, encryptedMnemonicSeed, encryptedHDSeed, isFromXRandom,
                      isSyncedComplete, null);
    }

    // Create With Random
    public HDAccount(SecureRandom random, CharSequence password, HDAccountGenerationDelegate generationDelegate) throws MnemonicException.MnemonicLengthException {
        isFromXRandom = random.getClass().getCanonicalName().indexOf("XRandom") >= 0;
        mnemonicSeed = new byte[16];
        random.nextBytes(mnemonicSeed);
        hdSeed = seedFromMnemonic(mnemonicSeed);
        EncryptedData encryptedHDSeed = new EncryptedData(hdSeed, password, isFromXRandom);
        EncryptedData encryptedMnemonicSeed = new EncryptedData(mnemonicSeed, password,
                                                                isFromXRandom);
        DeterministicKey master = HDKeyDerivation.createMasterPrivateKey(hdSeed);
        DeterministicKey account = getAccount(master);
        account.clearPrivateKey();
        initHDAccount(account, encryptedMnemonicSeed, encryptedHDSeed, isFromXRandom, true,
                      generationDelegate);
    }

    //use in import
    public HDAccount(EncryptedData encryptedMnemonicSeed, CharSequence password, boolean
            isSyncedComplete)
            throws MnemonicException.MnemonicLengthException {
        mnemonicSeed = encryptedMnemonicSeed.decrypt(password);
        hdSeed = seedFromMnemonic(mnemonicSeed);
        isFromXRandom = encryptedMnemonicSeed.isXRandom();
        EncryptedData encryptedHDSeed = new EncryptedData(hdSeed, password, isFromXRandom);
        DeterministicKey master = HDKeyDerivation.createMasterPrivateKey(hdSeed);
        DeterministicKey account = getAccount(master);
        account.clearPrivateKey();
        initHDAccount(account, encryptedMnemonicSeed, encryptedHDSeed, isFromXRandom,
                      isSyncedComplete, null);
    }

    public HDAccount(byte[] accountExtentedPub) throws MnemonicException.MnemonicLengthException {
        this(accountExtentedPub, false);
    }

    public HDAccount(byte[] accountExtentedPub, boolean isFromXRandom) throws MnemonicException
            .MnemonicLengthException {
        this(accountExtentedPub, isFromXRandom, true, null);
    }


    public HDAccount(byte[] accountExtentedPub, boolean isFromXRandom, boolean isSyncedComplete,
                     HDAccountGenerationDelegate generationDelegate) throws
            MnemonicException.MnemonicLengthException {
        super();
        this.isFromXRandom = isFromXRandom;
        DeterministicKey account = HDKeyDerivation.createMasterPubKeyFromExtendedBytes
                (accountExtentedPub);
        initHDAccount(account, null, null, isFromXRandom, isSyncedComplete, generationDelegate);
    }

    private void initHDAccount(DeterministicKey accountKey, EncryptedData encryptedMnemonicSeed,
                               EncryptedData encryptedHDSeed, boolean isFromXRandom, boolean
                                       isSyncedComplete, HDAccountGenerationDelegate
                                       generationDelegate) {
        this.isFromXRandom = isFromXRandom;
        double progress = 0;
        if (generationDelegate != null) {
            generationDelegate.onHDAccountGenerationProgress(progress);
        }
        String address = null;
        if (encryptedMnemonicSeed != null && mnemonicSeed != null) {
            ECKey k = new ECKey(mnemonicSeed, null);
            address = k.toAddress();
            k.clearPrivateKey();
        }

        DeterministicKey internalKey = getChainRootKey(accountKey, AbstractHD.PathType
                .INTERNAL_ROOT_PATH);
        DeterministicKey externalKey = getChainRootKey(accountKey, AbstractHD.PathType
                .EXTERNAL_ROOT_PATH);
        if (checkDuplicated(externalKey.getPubKeyExtended(), internalKey.getPubKeyExtended())) {
            throw new DuplicatedHDAccountException();
        }
        DeterministicKey key = externalKey.deriveSoftened(0);
        String firstAddress = key.toAddress();
        accountKey.wipe();

        progress += GenerationPreStartProgress;
        if (generationDelegate != null) {
            generationDelegate.onHDAccountGenerationProgress(progress);
        }

        double itemProgress = (1.0 - GenerationPreStartProgress) / (LOOK_AHEAD_SIZE * 2);

        List<HDAccountAddress> externalAddresses = new ArrayList<HDAccountAddress>();
        List<HDAccountAddress> internalAddresses = new ArrayList<HDAccountAddress>();
        for (int i = 0;
             i < LOOK_AHEAD_SIZE;
             i++) {
            byte[] subExternalPub = externalKey.deriveSoftened(i).getPubKey();
            HDAccountAddress externalAddress = new HDAccountAddress
                    (subExternalPub, AbstractHD.PathType.EXTERNAL_ROOT_PATH, i, isSyncedComplete,
                     hdSeedId);
            externalAddresses.add(externalAddress);
            progress += itemProgress;
            if (generationDelegate != null) {
                generationDelegate.onHDAccountGenerationProgress(progress);
            }

            byte[] subInternalPub = internalKey.deriveSoftened(i).getPubKey();
            HDAccountAddress internalAddress = new HDAccountAddress
                    (subInternalPub, AbstractHD.PathType.INTERNAL_ROOT_PATH, i, isSyncedComplete,
                     hdSeedId);
            internalAddresses.add(internalAddress);
            progress += itemProgress;
            if (generationDelegate != null) {
                generationDelegate.onHDAccountGenerationProgress(progress);
            }
        }
        if (encryptedMnemonicSeed == null) {
            hdSeedId = AbstractDb.hdAccountProvider.addMonitoredHDAccount(firstAddress,
                                                                          isFromXRandom, externalKey.getPubKeyExtended(), internalKey.getPubKeyExtended
                            ());
            hasSeed = false;
        } else {
            hdSeedId = AbstractDb.hdAccountProvider.addHDAccount(encryptedMnemonicSeed
                                                                         .toEncryptedString(), encryptedHDSeed.toEncryptedString(), firstAddress,
                                                                 isFromXRandom, address, externalKey.getPubKeyExtended(), internalKey
                                                                         .getPubKeyExtended());
            hasSeed = true;
        }
        for (HDAccountAddress addr : externalAddresses) {
            addr.setHdAccountId(hdSeedId);
        }
        for (HDAccountAddress addr : internalAddresses) {
            addr.setHdAccountId(hdSeedId);
        }
        AbstractDb.hdAccountAddressProvider.addAddress(externalAddresses);
        AbstractDb.hdAccountAddressProvider.addAddress(internalAddresses);
        internalKey.wipe();
        externalKey.wipe();
    }

    public HDAccount(int seedId) {
        this.hdSeedId = seedId;
        this.isFromXRandom = AbstractDb.hdAccountProvider.hdAccountIsXRandom(seedId);
        hasSeed = AbstractDb.hdAccountProvider.hasMnemonicSeed(this.hdSeedId);
        updateBalance();
    }

    @Override
    public String getFullEncryptPrivKey() {
        if (!hasPrivKey()) {
            return null;
        }
        String encryptPrivKey = getEncryptedMnemonicSeed();
        return PrivateKeyUtil.getFullencryptHDMKeyChain(isFromXRandom, encryptPrivKey);
    }

    public String getQRCodeFullEncryptPrivKey() {
        if (!hasPrivKey()) {
            return null;
        }
        return QRCodeUtil.HD_QR_CODE_FLAG + getFullEncryptPrivKey();
    }

    public byte[] getInternalPub() {
        return AbstractDb.hdAccountProvider.getInternalPub(hdSeedId);
    }

    public byte[] getExternalPub() {
        return AbstractDb.hdAccountProvider.getExternalPub(hdSeedId);
    }

    public String getFirstAddressFromDb() {
        return AbstractDb.hdAccountProvider.getHDFirstAddress(hdSeedId);
    }

    public void supplyEnoughKeys(boolean isSyncedComplete) {
        int lackOfExternal = issuedExternalIndex() + 1 + LOOK_AHEAD_SIZE -
                allGeneratedExternalAddressCount();
        if (lackOfExternal > 0) {
            supplyNewExternalKey(lackOfExternal, isSyncedComplete);
        }

        int lackOfInternal = issuedInternalIndex() + 1 + LOOK_AHEAD_SIZE -
                allGeneratedInternalAddressCount();
        if (lackOfInternal > 0) {
            supplyNewInternalKey(lackOfInternal, isSyncedComplete);
        }
    }

    private void supplyNewInternalKey(int count, boolean isSyncedComplete) {
        DeterministicKey root = HDKeyDerivation.createMasterPubKeyFromExtendedBytes
                (getInternalPub());
        int firstIndex = allGeneratedInternalAddressCount();
        ArrayList<HDAccountAddress> as = new ArrayList<HDAccountAddress>();
        for (int i = firstIndex;
             i < firstIndex + count;
             i++) {
            as.add(new HDAccountAddress(root.deriveSoftened(i).getPubKey(), AbstractHD.PathType
                    .INTERNAL_ROOT_PATH, i, isSyncedComplete, hdSeedId));
        }
        AbstractDb.hdAccountAddressProvider.addAddress(as);
        log.info("HD supplied {} internal addresses", as.size());
    }

    private void supplyNewExternalKey(int count, boolean isSyncedComplete) {
        DeterministicKey root = HDKeyDerivation.createMasterPubKeyFromExtendedBytes
                (getExternalPub());
        int firstIndex = allGeneratedExternalAddressCount();
        ArrayList<HDAccountAddress> as = new ArrayList<HDAccountAddress>();
        for (int i = firstIndex;
             i < firstIndex + count;
             i++) {
            as.add(new HDAccountAddress(root.deriveSoftened(i).getPubKey(), AbstractHD.PathType
                    .EXTERNAL_ROOT_PATH, i, isSyncedComplete, hdSeedId));
        }
        AbstractDb.hdAccountAddressProvider.addAddress(as);
        log.info("HD supplied {} external addresses", as.size());
    }

    protected String getEncryptedMnemonicSeed() {
        if (!hasPrivKey()) {
            return null;
        }
        return AbstractDb.hdAccountProvider.getHDAccountEncryptMnemonicSeed(hdSeedId);
    }

    protected String getEncryptedHDSeed() {
        if (!hasPrivKey()) {
            return null;
        }
        return AbstractDb.hdAccountProvider.getHDAccountEncryptSeed(hdSeedId);
    }

    @Override
    public String getAddress() {
        return AbstractDb.hdAccountAddressProvider.externalAddress(this.hdSeedId);
    }

    @Override
    public String getShortAddress() {
        return Utils.shortenAddress(getAddress());
    }

    public int issuedInternalIndex() {
        return AbstractDb.hdAccountAddressProvider.issuedIndex(this.hdSeedId, AbstractHD.PathType
                .INTERNAL_ROOT_PATH);
    }

    public int issuedExternalIndex() {
        return AbstractDb.hdAccountAddressProvider.issuedIndex(this.hdSeedId, AbstractHD.PathType
                .EXTERNAL_ROOT_PATH);
    }

    private int allGeneratedInternalAddressCount() {
        return AbstractDb.hdAccountAddressProvider.allGeneratedAddressCount(this.hdSeedId,
                                                                            AbstractHD.PathType.INTERNAL_ROOT_PATH);
    }

    private int allGeneratedExternalAddressCount() {
        return AbstractDb.hdAccountAddressProvider.allGeneratedAddressCount(this.hdSeedId,
                                                                            AbstractHD.PathType.EXTERNAL_ROOT_PATH);
    }

    public HDAccountAddress addressForPath(AbstractHD.PathType type, int index) {
        assert index < (type == AbstractHD.PathType.EXTERNAL_ROOT_PATH ?
                allGeneratedExternalAddressCount()
                : allGeneratedInternalAddressCount());
        return AbstractDb.hdAccountAddressProvider.addressForPath(this.hdSeedId, type, index);
    }

    public boolean requestNewReceivingAddress() {
        boolean result = AbstractDb.hdAccountAddressProvider.requestNewReceivingAddress(this.hdSeedId);
        if (result) {
            supplyEnoughKeys(true);
        }
        return result;
    }

    public void onNewTx(Tx tx, Tx.TxNotificationType txNotificationType) {
        supplyEnoughKeys(true);
        long deltaBalance = getDeltaBalance();
        AbstractApp.notificationService.notificatTx(hasPrivKey() ? HDAccountPlaceHolder :
                                                            HDAccountMonitoredPlaceHolder, tx, txNotificationType,
                                                    deltaBalance);
    }

    public boolean isTxRelated(Tx tx, List<String> inAddresses) {
        return getRelatedAddressesForTx(tx, inAddresses).size() > 0;
    }

    @Override
    public boolean initTxs(List<Tx> txs) {
        AbstractDb.txProvider.addTxs(txs);
        notificatTx(null, Tx.TxNotificationType.txFromApi);
        return true;
    }

    @Override
    public void notificatTx(Tx tx, Tx.TxNotificationType txNotificationType) {
        long deltaBalance = getDeltaBalance();
        AbstractApp.notificationService.notificatTx(hasPrivKey() ? HDAccountPlaceHolder :
                                                            HDAccountMonitoredPlaceHolder, tx, txNotificationType, deltaBalance);
    }

    private long getDeltaBalance() {
        long oldBalance = this.balance;
        this.updateBalance();
        return this.balance - oldBalance;
    }

    @Override
    public List<Tx> getTxs(int page) {
        return AbstractDb.hdAccountAddressProvider.getTxAndDetailByHDAccount(this.hdSeedId, page);
    }

    @Override
    public List<Tx> getTxs() {
        return AbstractDb.hdAccountAddressProvider.getTxAndDetailByHDAccount(this.hdSeedId);
    }

    @Override
    public int txCount() {
        return AbstractDb.hdAccountAddressProvider.hdAccountTxCount(this.hdSeedId);
    }

    @Override
    public void updateBalance() {
        this.balance = AbstractDb.hdAccountAddressProvider.getHDAccountConfirmedBalance(hdSeedId)
                + calculateUnconfirmedBalance();
    }

    private long calculateUnconfirmedBalance() {
        long balance = 0;

        List<Tx> txs = AbstractDb.hdAccountAddressProvider.getHDAccountUnconfirmedTx(this.hdSeedId);
        Collections.sort(txs);

        Set<byte[]> invalidTx = new HashSet<byte[]>();
        Set<OutPoint> spentOut = new HashSet<OutPoint>();
        Set<OutPoint> unspendOut = new HashSet<OutPoint>();

        for (int i = txs.size() - 1;
             i >= 0;
             i--) {
            Set<OutPoint> spent = new HashSet<OutPoint>();
            Tx tx = txs.get(i);

            Set<byte[]> inHashes = new HashSet<byte[]>();
            for (In in : tx.getIns()) {
                spent.add(new OutPoint(in.getPrevTxHash(), in.getPrevOutSn()));
                inHashes.add(in.getPrevTxHash());
            }

            if (tx.getBlockNo() == Tx.TX_UNCONFIRMED
                    && (Utils.isIntersects(spent, spentOut) || Utils.isIntersects(inHashes,
                                                                                  invalidTx))) {
                invalidTx.add(tx.getTxHash());
                continue;
            }

            spentOut.addAll(spent);
            HashSet<String> addressSet = getBelongAccountAddresses(tx.getOutAddressList());
            for (Out out : tx.getOuts()) {
                if (addressSet.contains(out.getOutAddress())) {
                    unspendOut.add(new OutPoint(tx.getTxHash(), out.getOutSn()));
                    balance += out.getOutValue();
                }
            }
            spent.clear();
            spent.addAll(unspendOut);
            spent.retainAll(spentOut);
            for (OutPoint o : spent) {
                Tx tx1 = AbstractDb.txProvider.getTxDetailByTxHash(o.getTxHash());
                unspendOut.remove(o);
                for (Out out : tx1.getOuts()) {
                    if (out.getOutSn() == o.getOutSn()) {
                        balance -= out.getOutValue();
                    }
                }
            }
        }
        return balance;
    }

    public List<HDAccountAddress> getRelatedAddressesForTx(Tx tx, List<String> inAddresses) {
        List<String> outAddressList = new ArrayList<String>();
        List<HDAccountAddress> hdAccountAddressList = new ArrayList<HDAccountAddress>();
        for (Out out : tx.getOuts()) {
            String outAddress = out.getOutAddress();
            outAddressList.add(outAddress);
        }
        List<HDAccountAddress> belongAccountOfOutList = AbstractDb.hdAccountAddressProvider
                .belongAccount(this.hdSeedId, outAddressList);
        if (belongAccountOfOutList != null
                && belongAccountOfOutList.size() > 0) {
            hdAccountAddressList.addAll(belongAccountOfOutList);
        }

        List<HDAccountAddress> belongAccountOfInList = getAddressFromIn(inAddresses);
        if (belongAccountOfInList != null && belongAccountOfInList.size() > 0) {
            hdAccountAddressList.addAll(belongAccountOfInList);
        }

        return hdAccountAddressList;
    }

    public HashSet<String> getBelongAccountAddresses(List<String> addressList) {
        return AbstractDb.hdAccountAddressProvider.getBelongAccountAddresses(this.hdSeedId, addressList);
    }

    public Tx newTx(String toAddress, Long amount, CharSequence password) throws TxBuilderException, MnemonicException.MnemonicLengthException, AddressFormatException {
        return newTx(new String[]{toAddress}, new Long[]{amount}, password);
    }

    public Tx newTx(String[] toAddresses, Long[] amounts, CharSequence password) throws TxBuilderException, MnemonicException.MnemonicLengthException, AddressFormatException {
        if (password != null && !hasPrivKey()) {
            throw new RuntimeException("Can not sign without private key");
        }
        Tx tx = newTx(toAddresses, amounts);
        List<HDAccountAddress> signingAddresses = getSigningAddressesForInputs(tx.getIns());
        assert signingAddresses.size() == tx.getIns().size();

        DeterministicKey master = masterKey(password);
        if (master == null) {
            return null;
        }
        DeterministicKey accountKey = getAccount(master);
        DeterministicKey external = getChainRootKey(accountKey, AbstractHD.PathType
                .EXTERNAL_ROOT_PATH);
        DeterministicKey internal = getChainRootKey(accountKey, AbstractHD.PathType
                .INTERNAL_ROOT_PATH);
        accountKey.wipe();
        master.wipe();
        List<byte[]> unsignedHashes = tx.getUnsignedInHashes();
        assert unsignedHashes.size() == signingAddresses.size();
        ArrayList<byte[]> signatures = new ArrayList<byte[]>();
        HashMap<String, DeterministicKey> addressToKeyMap = new HashMap<String, DeterministicKey>
                (signingAddresses.size());

        for (int i = 0;
             i < signingAddresses.size();
             i++) {
            HDAccountAddress a = signingAddresses.get(i);
            byte[] unsigned = unsignedHashes.get(i);

            if (!addressToKeyMap.containsKey(a.getAddress())) {
                if (a.getPathType() == AbstractHD.PathType.EXTERNAL_ROOT_PATH) {
                    addressToKeyMap.put(a.getAddress(), external.deriveSoftened(a.index));
                } else {
                    addressToKeyMap.put(a.getAddress(), internal.deriveSoftened(a.index));
                }
            }

            DeterministicKey key = addressToKeyMap.get(a.getAddress());
            assert key != null;

            TransactionSignature signature = new TransactionSignature(key.sign(unsigned, null),
                                                                      TransactionSignature.SigHash.ALL, false);
            signatures.add(ScriptBuilder.createInputScript(signature, key).getProgram());
        }

        tx.signWithSignatures(signatures);
        assert tx.verifySignatures();

        external.wipe();
        internal.wipe();
        for (DeterministicKey key : addressToKeyMap.values()) {
            key.wipe();
        }

        return tx;
    }

    public Tx newTx(String toAddress, Long amount) throws TxBuilderException, MnemonicException.MnemonicLengthException, AddressFormatException {
        return newTx(new String[]{toAddress}, new Long[]{amount});
    }


    public Tx newTx(String[] toAddresses, Long[] amounts) throws TxBuilderException, MnemonicException.MnemonicLengthException, AddressFormatException {
        List<Out> outs = AbstractDb.hdAccountAddressProvider.getUnspendOutByHDAccount(hdSeedId);
        Tx tx = TxBuilder.getInstance().buildTxFromAllAddress(outs, getNewChangeAddress(), Arrays
                .asList(amounts), Arrays.asList(toAddresses));
        return tx;
    }

    public List<HDAccountAddress> getSigningAddressesForInputs(List<In> inputs) {
        return AbstractDb.hdAccountAddressProvider.getSigningAddressesForInputs(this.hdSeedId, inputs);
    }

    public boolean isSendFromMe(List<String> addresses) {
        List<HDAccountAddress> hdAccountAddressList = getAddressFromIn(addresses);
        return hdAccountAddressList.size() > 0;
    }

    private List<HDAccountAddress> getAddressFromIn(List<String> addresses) {
        List<HDAccountAddress> hdAccountAddressList = AbstractDb.hdAccountAddressProvider
                .belongAccount(this.hdSeedId, addresses);
        return hdAccountAddressList;
    }

    public void updateIssuedInternalIndex(int index) {
        AbstractDb.hdAccountAddressProvider.updateIssuedIndex(this.hdSeedId, AbstractHD.PathType.INTERNAL_ROOT_PATH,
                                                              index);
    }

    public void updateIssuedExternalIndex(int index) {
        AbstractDb.hdAccountAddressProvider.updateIssuedIndex(this.hdSeedId, AbstractHD.PathType.EXTERNAL_ROOT_PATH,
                                                              index);
    }

    private String getNewChangeAddress() {
        return addressForPath(AbstractHD.PathType.INTERNAL_ROOT_PATH, issuedInternalIndex() + 1)
                .getAddress();
    }


    public void updateSyncComplete(HDAccountAddress accountAddress) {
        AbstractDb.hdAccountAddressProvider.updateSyncdComplete(this.hdSeedId, accountAddress);
    }

    public int elementCountForBloomFilter() {
        return allGeneratedExternalAddressCount() * 2 + AbstractDb.hdAccountAddressProvider
                .getUnspendOutCountByHDAccountWithPath(getHdSeedId(), AbstractHD.PathType
                        .INTERNAL_ROOT_PATH) + AbstractDb.hdAccountAddressProvider
                .getUnconfirmedSpentOutCountByHDAccountWithPath(getHdSeedId(), AbstractHD
                        .PathType.INTERNAL_ROOT_PATH);
    }

    public void addElementsForBloomFilter(BloomFilter filter) {
        List<byte[]> pubs = AbstractDb.hdAccountAddressProvider.getPubs(this.hdSeedId, AbstractHD
                .PathType.EXTERNAL_ROOT_PATH);
        for (byte[] pub : pubs) {
            filter.insert(pub);
            filter.insert(Utils.sha256hash160(pub));
        }
        List<Out> outs = AbstractDb.hdAccountAddressProvider.getUnspendOutByHDAccountWithPath
                (getHdSeedId(), AbstractHD.PathType.INTERNAL_ROOT_PATH);
        for (Out out : outs) {
            filter.insert(out.getOutpointData());
        }
        outs = AbstractDb.hdAccountAddressProvider.getUnconfirmedSpentOutByHDAccountWithPath
                (getHdSeedId(), AbstractHD.PathType.INTERNAL_ROOT_PATH);
        for (Out out : outs) {
            filter.insert(out.getOutpointData());
        }
    }

    @Override
    public long getBalance() {
        return balance;
    }

    @Override
    public boolean isSyncComplete() {
        int unsyncedAddressCount = AbstractDb.hdAccountAddressProvider.unSyncedAddressCount(this.hdSeedId);
        return unsyncedAddressCount == 0;
    }

    @Override
    public List<Tx> getRecentlyTxsWithConfirmationCntLessThan(int confirmationCnt, int limit) {
        List<Tx> txList = new ArrayList<Tx>();
        int blockNo = BlockChain.getInstance().getLastBlock().getBlockNo() - confirmationCnt + 1;
        for (Tx tx : AbstractDb.hdAccountAddressProvider.getRecentlyTxsByAccount(this.hdSeedId, blockNo, limit)) {
            txList.add(tx);
        }
        return txList;
    }

    @Override
    public Tx buildTx(String changeAddress, List<Long> amounts, List<String> addresses) {
        throw new RuntimeException("use newTx() for hdAccountHot");
    }

    @Override
    public boolean hasPrivKey() {
        return hasSeed;
    }

    @Override
    public long getSortTime() {
        return 0;
    }

    @Override
    public String getEncryptPrivKeyOfDb() {
        return null;
    }

    @Override
    public String getFullEncryptPrivKeyOfDb() {
        return null;
    }

    protected DeterministicKey getChainRootKey(DeterministicKey accountKey, AbstractHD.PathType
            pathType) {
        return accountKey.deriveSoftened(pathType.getValue());
    }

    protected DeterministicKey getAccount(DeterministicKey master) {
        DeterministicKey purpose = master.deriveHardened(44);
        DeterministicKey coinType = purpose.deriveHardened(0);
        DeterministicKey account = coinType.deriveHardened(0);
        purpose.wipe();
        coinType.wipe();
        return account;
    }

    protected DeterministicKey masterKey(CharSequence password) throws MnemonicException
            .MnemonicLengthException {
        long begin = System.currentTimeMillis();
        decryptHDSeed(password);
        DeterministicKey master = HDKeyDerivation.createMasterPrivateKey(hdSeed);
        wipeHDSeed();
        log.info("hdm keychain decrypt time: {}", System.currentTimeMillis() - begin);
        return master;
    }

    protected void decryptHDSeed(CharSequence password) throws MnemonicException
            .MnemonicLengthException {
        if (hdSeedId < 0 || password == null) {
            return;
        }
        String encryptedHDSeed = getEncryptedHDSeed();
        if (!Utils.isEmpty(encryptedHDSeed)) {
            hdSeed = new EncryptedData(encryptedHDSeed).decrypt(password);
        }
    }

    public void decryptMnemonicSeed(CharSequence password) throws KeyCrypterException {
        if (hdSeedId < 0) {
            return;
        }
        String encrypted = getEncryptedMnemonicSeed();
        if (!Utils.isEmpty(encrypted)) {
            mnemonicSeed = new EncryptedData(encrypted).decrypt(password);
        }
    }

    public List<String> getSeedWords(CharSequence password) throws MnemonicException
            .MnemonicLengthException {
        decryptMnemonicSeed(password);
        List<String> words = MnemonicCode.instance().toMnemonic(mnemonicSeed);
        wipeMnemonicSeed();
        return words;
    }

    public boolean checkWithPassword(CharSequence password) {
        if (!hasPrivKey()) {
            return true;
        }
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

    protected String getFirstAddressFromSeed(CharSequence password) {
        DeterministicKey key = getExternalKey(0, password);
        String address = Utils.toAddress(key.getPubKeyHash());
        key.wipe();
        return address;
    }

    public DeterministicKey getExternalKey(int index, CharSequence password) {
        try {
            DeterministicKey master = masterKey(password);
            DeterministicKey accountKey = getAccount(master);
            DeterministicKey externalChainRoot = getChainRootKey(accountKey, AbstractHD.PathType
                    .EXTERNAL_ROOT_PATH);
            DeterministicKey key = externalChainRoot.deriveSoftened(index);
            master.wipe();
            accountKey.wipe();
            externalChainRoot.wipe();
            return key;
        } catch (KeyCrypterException e) {
            throw new PasswordException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    protected void wipeHDSeed() {
        if (hdSeed == null) {
            return;
        }
        Utils.wipeBytes(hdSeed);
    }

    protected void wipeMnemonicSeed() {
        if (mnemonicSeed == null) {
            return;
        }
        Utils.wipeBytes(mnemonicSeed);
    }

    public int getHdSeedId() {
        return hdSeedId;
    }

    public static final byte[] seedFromMnemonic(byte[] mnemonicSeed) throws MnemonicException
            .MnemonicLengthException {
        return MnemonicCode.toSeed(MnemonicCode.instance().toMnemonic(mnemonicSeed), "");
    }

    @Override
    public boolean isFromXRandom() {
        return isFromXRandom;
    }

    public static class HDAccountAddress {
        private String address;
        private byte[] pub;
        private int index;
        private AbstractHD.PathType pathType;
        private boolean isSyncedComplete;
        private boolean isIssued;


        private int hdAccountId;

        public HDAccountAddress(byte[] pub, AbstractHD.PathType pathType, int index, boolean
                isSyncedComplete, int hdAccountId) {
            this(Utils.toAddress(Utils.sha256hash160(pub)), pub, pathType, index, false,
                 isSyncedComplete, hdAccountId);
        }

        public HDAccountAddress(String address, byte[] pub, AbstractHD.PathType pathType, int
                index, boolean isIssued, boolean isSyncedComplete, int hdAccountId) {
            this.pub = pub;
            this.address = address;
            this.pathType = pathType;
            this.index = index;
            this.isIssued = isIssued;
            this.isSyncedComplete = isSyncedComplete;
            this.hdAccountId = hdAccountId;
        }

        public String getAddress() {
            return address;
        }

        public byte[] getPub() {
            return pub;
        }

        public int getIndex() {
            return index;
        }

        public AbstractHD.PathType getPathType() {
            return pathType;
        }

        public boolean isIssued() {
            return isIssued;
        }

        public boolean isSyncedComplete() {
            return isSyncedComplete;
        }

        public void setIssued(boolean isIssued) {
            this.isIssued = isIssued;
        }

        public void setSyncedComplete(boolean isSynced) {
            this.isSyncedComplete = isSynced;
        }


        public int getHdAccountId() {
            return hdAccountId;
        }

        public void setHdAccountId(int hdAccountId) {
            this.hdAccountId = hdAccountId;
        }
    }

    public static final boolean checkDuplicated(byte[] ex, byte[] in) {
        return AbstractDb.hdAccountProvider.isPubExist(ex, in);
    }

    public static class DuplicatedHDAccountException extends RuntimeException {

    }
}
