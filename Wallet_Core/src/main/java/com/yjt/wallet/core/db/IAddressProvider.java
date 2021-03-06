package com.yjt.wallet.core.db;

import com.yjt.wallet.core.Address;
import com.yjt.wallet.core.HDMAddress;
import com.yjt.wallet.core.HDMBId;
import com.yjt.wallet.core.HDMKeychain;
import com.yjt.wallet.core.crypto.PasswordSeed;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface IAddressProvider {
    // password
    boolean changePassword(CharSequence oldPassword, CharSequence newPassword);

    PasswordSeed getPasswordSeed();

    boolean hasPasswordSeed();

    // hdm
    List<Integer> getHDSeeds();

    String getEncryptMnemonicSeed(int hdSeedId);

    String getEncryptHDSeed(int hdSeedId);


    void updateEncrypttMnmonicSeed(int hdSeedId, String encryptMnmonicSeed);

    boolean isHDSeedFromXRandom(int hdSeedId);

    String getHDMFristAddress(int hdSeedId);

    String getSingularModeBackup(int hdSeedId);

    void setSingularModeBackup(int hdSeedId, String singularModeBackup);

    int addHDKey(String encryptedMnemonicSeed, String encryptHdSeed, String firstAddress, boolean isXrandom, String addressOfPS);

    int addEnterpriseHDKey(String encryptedMnemonicSeed, String encryptHdSeed, String firstAddress, boolean isXrandom, String addressOfPS);

    HDMBId getHDMBId();

    void addAndUpdateHDMBId(HDMBId bitherId, String addressOfPS);

    List<HDMAddress> getHDMAddressInUse(HDMKeychain keychain);

    void prepareHDMAddresses(int hdSeedId, List<HDMAddress.Pubs> pubs);

    List<HDMAddress.Pubs> getUncompletedHDMAddressPubs(int hdSeedId, int count);

    int maxHDMAddressPubIndex(int hdSeedId);//including completed and uncompleted

    void recoverHDMAddresses(int hdSeedId, List<HDMAddress> addresses);


    void completeHDMAddresses(int hdSeedId, List<HDMAddress> addresses);

    void setHDMPubsRemote(int hdSeedId, int index, byte[] remote);

    int uncompletedHDMAddressCount(int hdSeedId);

    void syncComplete(int hdSeedId, int hdSeedIndex);

    //normal
    List<Address> getAddresses();

    String getEncryptPrivateKey(String address);

    void addAddress(Address address);

    void updatePrivateKey(String address, String encryptPriv);

    void removeWatchOnlyAddress(Address address);

    void trashPrivKeyAddress(Address address);

    void restorePrivKeyAddress(Address address);

    void updateSyncComplete(Address address);

    // alias
    Map<String, String> getAliases();

    void updateAlias(String address, @Nullable String alias);

    //vanity_len
    Map<String, Integer> getVanitylens();

    void updateVaitylen(String address, int vanitylen);


}
