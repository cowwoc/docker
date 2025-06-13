package com.github.cowwoc.anchor4j.docker.test;

import com.github.cowwoc.anchor4j.core.internal.util.Paths;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;

public final class TestSuite
{
	private static final Path CERTIFICATES_DIRECTORY = Path.of("certs");

	/**
	 * Create all the docker-in-docker containers that will be used to run tests.
	 */
	@BeforeSuite
	public void createCertificate() throws NoSuchAlgorithmException, IOException, CertificateException,
		OperatorCreationException
	{
		Security.addProvider(new BouncyCastleProvider());
		Paths.deleteRecursively(CERTIFICATES_DIRECTORY);

		// 1. Generate CA
		KeyPair caKeyPair = generateKeyPair();
		X509Certificate caCert = generateCertificate("CN=Docker CA", caKeyPair, caKeyPair.getPrivate(), true);

		Files.createDirectories(CERTIFICATES_DIRECTORY);
		Path caDirectory = CERTIFICATES_DIRECTORY.resolve("ca");
		Path clientDirectory = CERTIFICATES_DIRECTORY.resolve("client");
		Path serverDirectory = CERTIFICATES_DIRECTORY.resolve("server");
		Files.createDirectories(caDirectory);
		Files.createDirectories(clientDirectory);
		Files.createDirectories(serverDirectory);

		// We intentionally avoid creating ca/key.pem because dockerd-entrypoint.sh will only generate
		// client and server certificates if this key file is present. Omitting it prevents automatic TLS
		// generation.
		//
//		writePem(caDirectory.resolve("key.pem").toString(), caKeyPair.getPrivate());
		writePem(caDirectory.resolve("cert.pem").toString(), caCert);
		Files.copy(caDirectory.resolve("cert.pem"), serverDirectory.resolve("ca.pem"));
		Files.copy(caDirectory.resolve("cert.pem"), clientDirectory.resolve("ca.pem"));

		// 2. Generate Server Certificate signed by CA
		KeyPair serverKeyPair = generateKeyPair();
		X509Certificate serverCert = generateCertificate("CN=docker-server", serverKeyPair,
			caKeyPair.getPrivate(), false, caCert);

		writePem(serverDirectory.resolve("key.pem").toString(), serverKeyPair.getPrivate());
		writePem(serverDirectory.resolve("cert.pem").toString(), serverCert);

		// 3. Generate Client Certificate signed by CA (optional)
		KeyPair clientKeyPair = generateKeyPair();
		X509Certificate clientCert = generateCertificate("CN=docker-client", clientKeyPair,
			caKeyPair.getPrivate(), false, caCert);

		writePem(clientDirectory.resolve("key.pem").toString(), clientKeyPair.getPrivate());
		writePem(clientDirectory.resolve("cert.pem").toString(), clientCert);
	}

	private static KeyPair generateKeyPair() throws NoSuchAlgorithmException
	{
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
		keyGen.initialize(2048);
		return keyGen.generateKeyPair();
	}

	private static X509Certificate generateCertificate(String dn, KeyPair pair, PrivateKey signerKey,
		boolean isCA) throws CertificateException, OperatorCreationException, NoSuchAlgorithmException, CertIOException
	{
		return generateCertificate(dn, pair, signerKey, isCA, null);
	}

	private static X509Certificate generateCertificate(String dn, KeyPair subjectKeyPair, PrivateKey signerKey,
		boolean isCA, X509Certificate caCert)
		throws CertIOException, NoSuchAlgorithmException, OperatorCreationException, CertificateException
	{
		long now = System.currentTimeMillis();
		Date from = new Date(now);
		Calendar cal = Calendar.getInstance();
		cal.setTime(from);
		cal.add(Calendar.YEAR, 10);
		Date to = cal.getTime();

		X500Name issuer;
		if (caCert == null)
			issuer = new X500Name(dn);
		else
			issuer = new X500Name(caCert.getSubjectX500Principal().getName());
		X500Name subject = new X500Name(dn);

		BigInteger serial = BigInteger.valueOf(now);

		X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(issuer, serial, from, to, subject,
			subjectKeyPair.getPublic());

		// Add Basic Constraints
		certBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.basicConstraints, true,
			new BasicConstraints(isCA));

		// Add Key Usage
		if (isCA)
		{
			certBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.keyUsage, true,
				new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
		}
		else
		{
			certBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.keyUsage, true,
				new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));

			GeneralName san = new GeneralName(GeneralName.dNSName, "localhost");
			GeneralNames subjectAltName = new GeneralNames(san);
			certBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAltName);
		}

		// Add Subject Key Identifier (optional, but good practice)
		JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
		certBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.subjectKeyIdentifier, false,
			extUtils.createSubjectKeyIdentifier(subjectKeyPair.getPublic()));

		ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(signerKey);

		X509CertificateHolder certHolder = certBuilder.build(signer);

		return new JcaX509CertificateConverter().setProvider("BC").getCertificate(certHolder);
	}

	private static void writePem(String filename, Object obj) throws IOException
	{
		try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(filename)))
		{
			writer.writeObject(obj);
		}
	}

	/**
	 * Removes all the containers used by the tests.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	@AfterSuite
	public void removeContainers() throws IOException
	{
		Paths.deleteRecursively(CERTIFICATES_DIRECTORY);
	}
}