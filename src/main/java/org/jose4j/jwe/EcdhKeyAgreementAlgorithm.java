package org.jose4j.jwe;

import org.jose4j.jwa.AlgorithmInfo;
import org.jose4j.jwe.kdf.KdfUtil;
import org.jose4j.jwk.EcJwkGenerator;
import org.jose4j.jwk.EllipticCurveJsonWebKey;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwx.HeaderParameterNames;
import org.jose4j.jwx.Headers;
import org.jose4j.keys.KeyPersuasion;
import org.jose4j.lang.ByteUtil;
import org.jose4j.lang.JoseException;
import org.jose4j.lang.UncheckedJoseException;

import javax.crypto.KeyAgreement;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.interfaces.ECPublicKey;

/**
 */
public class EcdhKeyAgreementAlgorithm extends AlgorithmInfo implements KeyManagementAlgorithm
{
    public EcdhKeyAgreementAlgorithm()
    {
        setAlgorithmIdentifier(KeyManagementAlgorithmIdentifiers.ECDH_ES);
        setJavaAlgorithm("ECDH");
        setKeyType(EllipticCurveJsonWebKey.KEY_TYPE);
        setKeyPersuasion(KeyPersuasion.ASYMMETRIC);
    }

    public ContentEncryptionKeys manageForEncrypt(Key managementKey, ContentEncryptionKeyDescriptor cekDesc, Headers headers) throws JoseException
    {
        ECPublicKey receiversKey = (ECPublicKey) managementKey;
        EllipticCurveJsonWebKey ephemeralJwk = EcJwkGenerator.generateJwk(receiversKey.getParams());
        return manageForEncrypt(managementKey, cekDesc, headers, ephemeralJwk);
    }

    ContentEncryptionKeys manageForEncrypt(Key managementKey, ContentEncryptionKeyDescriptor cekDesc, Headers headers, PublicJsonWebKey ephemeralJwk) throws JoseException
    {
        headers.setJwkHeaderValue(HeaderParameterNames.EPHEMERAL_PUBLIC_KEY, ephemeralJwk);
        byte[] z = generateEcdhSecret(ephemeralJwk.getPrivateKey(), (PublicKey) managementKey);
        byte[] derivedKey = kdf(cekDesc, headers, z);
        return new ContentEncryptionKeys(derivedKey, null);
    }

    public Key manageForDecrypt(Key managementKey, byte[] encryptedKey, ContentEncryptionKeyDescriptor cekDesc, Headers headers) throws JoseException
    {
        JsonWebKey ephemeralJwk = headers.getJwkHeaderValue(HeaderParameterNames.EPHEMERAL_PUBLIC_KEY);
        ephemeralJwk.getKey();
        byte[] z = generateEcdhSecret((PrivateKey) managementKey, (PublicKey)ephemeralJwk.getKey());
        byte[] derivedKey = kdf(cekDesc, headers, z);
        String cekAlg = cekDesc.getContentEncryptionKeyAlgorithm();
        return new SecretKeySpec(derivedKey, cekAlg);
    }

    private byte[] kdf(ContentEncryptionKeyDescriptor cekDesc, Headers headers, byte[] z)
    {
        KdfUtil kdf = new KdfUtil();
        int keydatalen = ByteUtil.bitLength(cekDesc.getContentEncryptionKeyByteLength());
        /*
           AlgorithmID  In the Direct Key Agreement case, this is set to the
          octets of the UTF-8 representation of the "enc" header parameter
          value.  In the Key Agreement with Key Wrapping case, this is set
          to the octets of the UTF-8 representation of the "alg" header
          parameter value.*/
        String algorithmID = headers.getStringHeaderValue(HeaderParameterNames.ENCRYPTION_METHOD);
        String partyUInfo = headers.getStringHeaderValue(HeaderParameterNames.AGREEMENT_PARTY_U_INFO);
        String partyVInfo = headers.getStringHeaderValue(HeaderParameterNames.AGREEMENT_PARTY_V_INFO);
        return kdf.kdf(z, keydatalen, algorithmID, partyUInfo, partyVInfo);
    }


    private KeyAgreement getKeyAgreement()
    {
        try
        {
            return KeyAgreement.getInstance(getJavaAlgorithm());
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new UncheckedJoseException("No " + getJavaAlgorithm() + " KeyAgreement available.", e);
        }
    }

    private byte[] generateEcdhSecret(PrivateKey privateKey, PublicKey publicKey) throws JoseException
    {
        KeyAgreement keyAgreement = getKeyAgreement();

        try
        {
            keyAgreement.init(privateKey);
            keyAgreement.doPhase(publicKey, true);
        }
        catch (InvalidKeyException e)
        {
            throw new JoseException("Invalid Key for " + getJavaAlgorithm() + " key agreement." ,e);
        }

        return keyAgreement.generateSecret();
    }
}