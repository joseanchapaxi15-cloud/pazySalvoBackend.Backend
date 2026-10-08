package ec.inamhi.pazysalvo.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

@Service
public class Pkcs12PdfSigner {

    public byte[] sign(byte[] pdf, byte[] pkcs12, char[] password, String signerName, String signerEmail) {
        if (pdf == null || pdf.length < 5 || pdf[0] != '%' || pdf[1] != 'P'
            || pdf[2] != 'D' || pdf[3] != 'F' || pdf[4] != '-') {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo preparar el PDF interno para la firma.");
        }
        KeyStore keyStore;
        PrivateKey privateKey = null;
        X509Certificate signingCertificate = null;
        List<X509Certificate> certificateChain = new ArrayList<>();
        try {
            keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new ByteArrayInputStream(pkcs12), password);
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (!keyStore.isKeyEntry(alias)) continue;
                Key candidate = keyStore.getKey(alias, password);
                Certificate[] chain = keyStore.getCertificateChain(alias);
                if (!(candidate instanceof PrivateKey) || chain == null || chain.length == 0
                    || !(chain[0] instanceof X509Certificate leaf)) continue;
                privateKey = (PrivateKey) candidate;
                signingCertificate = leaf;
                for (Certificate certificate : chain) {
                    if (certificate instanceof X509Certificate x509) certificateChain.add(x509);
                }
                break;
            }
            if (privateKey == null || signingCertificate == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El archivo P12 no contiene una clave privada y su certificado.");
            }
            signingCertificate.checkValidity();
            validateKeyUsage(signingCertificate);
            return signPdf(pdf, privateKey, signingCertificate, certificateChain, signerName, signerEmail);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException | GeneralSecurityException | org.bouncycastle.operator.OperatorCreationException
                 | org.bouncycastle.cms.CMSException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No se pudo abrir el certificado P12. Verifica el archivo, la contraseña y la vigencia del certificado.", exception);
        }
    }

    private byte[] signPdf(byte[] pdf, PrivateKey privateKey, X509Certificate certificate,
                           List<X509Certificate> chain, String signerName, String signerEmail)
        throws IOException, org.bouncycastle.operator.OperatorCreationException,
        org.bouncycastle.cms.CMSException, GeneralSecurityException {
        String algorithm = switch (privateKey.getAlgorithm().toUpperCase(Locale.ROOT)) {
            case "RSA" -> "SHA256withRSA";
            case "EC", "ECDSA" -> "SHA256withECDSA";
            case "DSA" -> "SHA256withDSA";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El algoritmo de clave del certificado no es compatible para firmar PDF.");
        };

        try (PDDocument document = Loader.loadPDF(pdf); ByteArrayOutputStream output = new ByteArrayOutputStream();
             SignatureOptions options = new SignatureOptions()) {
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName(signerName);
            signature.setLocation("INAMHI");
            signature.setReason("Firma electrónica del apartado Paz y Salvo - " + signerEmail);
            signature.setSignDate(Calendar.getInstance());
            options.setPreferredSignatureSize(SignatureOptions.DEFAULT_SIGNATURE_SIZE * 2);

            document.addSignature(signature, content -> createCms(content.readAllBytes(), privateKey,
                certificate, chain, algorithm), options);
            document.saveIncremental(output);
            return output.toByteArray();
        }
    }

    private byte[] createCms(byte[] content, PrivateKey privateKey, X509Certificate certificate,
                             List<X509Certificate> chain, String algorithm)
        throws org.bouncycastle.operator.OperatorCreationException, org.bouncycastle.cms.CMSException,
        java.security.cert.CertificateEncodingException, IOException {
        CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
        var contentSigner = new JcaContentSignerBuilder(algorithm).build(privateKey);
        var signerInfo = new JcaSignerInfoGeneratorBuilder(
            new JcaDigestCalculatorProviderBuilder().build()).build(contentSigner, certificate);
        generator.addSignerInfoGenerator(signerInfo);
        generator.addCertificates(new JcaCertStore(chain));
        return generator.generate(new CMSProcessableByteArray(content), false).getEncoded();
    }

    private void validateKeyUsage(X509Certificate certificate) {
        boolean[] usage = certificate.getKeyUsage();
        if (usage != null && !(usage.length > 0 && usage[0]) && !(usage.length > 1 && usage[1])) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "El certificado no permite firma digital o no repudio.");
        }
    }
}
