/* ==================================================================
 * DefaultKeystoreService.java - Dec 5, 2012 9:10:53 AM
 * 
 * Copyright 2007-2012 SolarNetwork.net Dev Team
 * 
 * This program is free software; you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as 
 * published by the Free Software Foundation; either version 2 of 
 * the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with this program; if not, write to the Free Software 
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 
 * 02111-1307 USA
 * ==================================================================
 */

package net.solarnetwork.node.setup.impl;

import static net.solarnetwork.node.SetupSettings.KEY_CONFIRMATION_CODE;
import static net.solarnetwork.node.SetupSettings.SETUP_TYPE_KEY;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.security.auth.x500.X500Principal;
import net.solarnetwork.node.dao.SettingDao;
import net.solarnetwork.node.setup.PKIService;
import net.solarnetwork.support.CertificateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for managing a {@link KeyStore}.
 * 
 * @author matt
 * @version 1.0
 */
public class DefaultKeystoreService implements PKIService {

	public static final String DEFAULT_KEY_STORE_PATH = "conf/pki/node.jks";

	private String keyStorePath = DEFAULT_KEY_STORE_PATH;
	private String nodeAlias = "node";
	private String caAlias = "ca";
	private int keySize = 2048;
	private String manualKeyStorePassword;

	@Resource
	private CertificateService certificateService;

	@Resource
	private SettingDao settingDao;

	private final Logger log = LoggerFactory.getLogger(getClass());

	@PostConstruct
	public void init() {
		setSystemKeyStoreProperties();
	}

	private String getKeyStorePassword() {
		return (manualKeyStorePassword != null && manualKeyStorePassword.length() > 0 ? manualKeyStorePassword
				: getSetting(KEY_CONFIRMATION_CODE));
	}

	private void setSystemKeyStoreProperties() {
		final String pass = getKeyStorePassword();
		if ( pass != null ) {
			System.setProperty("javax.net.ssl.trustStore", keyStorePath);
			System.setProperty("javax.net.ssl.trustStorePassword", pass);
			System.setProperty("javax.net.ssl.keyStore", keyStorePath);
			System.setProperty("javax.net.ssl.keyStorePassword", pass);
		}
	}

	@Override
	public boolean isNodeCertificateValid(String issuerDN)
			throws net.solarnetwork.support.CertificateException {
		KeyStore keyStore = loadKeyStore();
		X509Certificate x509 = null;
		try {
			if ( keyStore == null || !keyStore.containsAlias(nodeAlias) ) {
				return false;
			}
			Certificate cert = keyStore.getCertificate(nodeAlias);
			if ( !(cert instanceof X509Certificate) ) {
				return false;
			}
			x509 = (X509Certificate) cert;
			x509.checkValidity();
			X500Principal issuer = new X500Principal(issuerDN);
			if ( !x509.getIssuerX500Principal().equals(issuer) ) {
				log.debug("Certificate issuer {} not same as expected {}", x509.getIssuerX500Principal()
						.getName(), issuer.getName());
				return false;
			}
			return true;
		} catch ( KeyStoreException e ) {
			throw new net.solarnetwork.support.CertificateException(
					"Error checking for node certificate", e);
		} catch ( CertificateExpiredException e ) {
			log.debug("Certificate {} has expired", x509.getSubjectDN().getName());
		} catch ( CertificateNotYetValidException e ) {
			log.debug("Certificate {} not valid yet", x509.getSubjectDN().getName());
		}
		return false;
	}

	@Override
	public X509Certificate generateNodeSelfSignedCertificate(String dn)
			throws net.solarnetwork.support.CertificateException {
		KeyStore keyStore = loadKeyStore();
		return createSelfSignedCertificate(keyStore, dn, nodeAlias);
	}

	private X509Certificate createSelfSignedCertificate(KeyStore keyStore, String dn, String alias) {
		try {
			// create new key pair for the node
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
			keyGen.initialize(keySize, new SecureRandom());
			KeyPair keypair = keyGen.generateKeyPair();
			PublicKey publicKey = keypair.getPublic();
			PrivateKey privateKey = keypair.getPrivate();

			Certificate cert = certificateService.generateCertificate(dn, publicKey, privateKey);
			keyStore.setKeyEntry(alias, privateKey, new char[0], new Certificate[] { cert });
			saveKeyStore(keyStore);
			return (X509Certificate) cert;
		} catch ( NoSuchAlgorithmException e ) {
			throw new net.solarnetwork.support.CertificateException("Error setting up node key pair", e);
		} catch ( KeyStoreException e ) {
			throw new net.solarnetwork.support.CertificateException("Error setting up node key pair", e);
		}
	}

	private void saveTrustedCertificate(X509Certificate cert, String alias) {
		KeyStore keyStore = loadKeyStore();
		try {
			keyStore.setCertificateEntry(alias, cert);
			saveKeyStore(keyStore);
		} catch ( KeyStoreException e ) {
			throw new net.solarnetwork.support.CertificateException("Error saving trusted certificate",
					e);
		}
	}

	@Override
	public void saveCACertificate(X509Certificate cert)
			throws net.solarnetwork.support.CertificateException {
		saveTrustedCertificate(cert, caAlias);
	}

	@Override
	public String generateNodePKCS10CertificateRequestString()
			throws net.solarnetwork.support.CertificateException {
		KeyStore keyStore = loadKeyStore();
		Key key;
		try {
			key = keyStore.getKey(nodeAlias, new char[0]);
		} catch ( UnrecoverableKeyException e ) {
			throw new net.solarnetwork.support.CertificateException("Error opening node private key", e);
		} catch ( KeyStoreException e ) {
			throw new net.solarnetwork.support.CertificateException("Error opening node private key", e);
		} catch ( NoSuchAlgorithmException e ) {
			throw new net.solarnetwork.support.CertificateException("Error opening node private key", e);
		}
		assert key instanceof PrivateKey;
		Certificate cert;
		try {
			cert = keyStore.getCertificate(nodeAlias);
		} catch ( KeyStoreException e ) {
			throw new net.solarnetwork.support.CertificateException("Error opening node certificate", e);
		}
		assert cert instanceof X509Certificate;
		return certificateService.generatePKCS10CertificateRequestString((X509Certificate) cert,
				(PrivateKey) key);
	}

	@Override
	public X509Certificate getNodeCertificate() throws net.solarnetwork.support.CertificateException {
		return getNodeCertificate(loadKeyStore());
	}

	private X509Certificate getNodeCertificate(KeyStore keyStore) {
		X509Certificate nodeCert;
		try {
			nodeCert = (X509Certificate) keyStore.getCertificate(nodeAlias);
		} catch ( KeyStoreException e ) {
			throw new net.solarnetwork.support.CertificateException("Error opening node certificate", e);
		}
		return nodeCert;
	}

	@Override
	public X509Certificate getCACertificate() throws net.solarnetwork.support.CertificateException {
		return getCACertificate(loadKeyStore());
	}

	private X509Certificate getCACertificate(KeyStore keyStore) {
		X509Certificate nodeCert;
		try {
			nodeCert = (X509Certificate) keyStore.getCertificate(caAlias);
		} catch ( KeyStoreException e ) {
			throw new net.solarnetwork.support.CertificateException("Error opening node certificate", e);
		}
		return nodeCert;
	}

	@Override
	public void saveNodeSignedCertificate(X509Certificate signedCert)
			throws net.solarnetwork.support.CertificateException {
		KeyStore keyStore = loadKeyStore();
		Key key;
		try {
			key = keyStore.getKey(nodeAlias, new char[0]);
		} catch ( UnrecoverableKeyException e ) {
			throw new net.solarnetwork.support.CertificateException("Error opening node private key", e);
		} catch ( KeyStoreException e ) {
			throw new net.solarnetwork.support.CertificateException("Error opening node private key", e);
		} catch ( NoSuchAlgorithmException e ) {
			throw new net.solarnetwork.support.CertificateException("Error opening node private key", e);
		}
		X509Certificate nodeCert = getNodeCertificate(keyStore);
		X509Certificate caCert = getCACertificate(keyStore);

		// the issuer must be our CA cert subject...
		if ( !signedCert.getIssuerDN().equals(caCert.getSubjectDN()) ) {
			throw new net.solarnetwork.support.CertificateException("Issuer "
					+ signedCert.getIssuerDN().getName() + " does not match expected "
					+ caCert.getSubjectDN().getName());
		}

		// the subject must be our node's existing subject...
		if ( !signedCert.getSubjectDN().equals(nodeCert.getSubjectDN()) ) {
			throw new net.solarnetwork.support.CertificateException("Subject "
					+ signedCert.getIssuerDN().getName() + " does not match expected "
					+ nodeCert.getSubjectDN().getName());
		}

		log.info("Saving signed node certificate reply {} issued by {}", signedCert.getSubjectDN()
				.getName(), signedCert.getIssuerDN().getName());
		try {
			keyStore.setKeyEntry(nodeAlias, key, new char[0], new Certificate[] { signedCert, caCert });
		} catch ( KeyStoreException e ) {
			throw new net.solarnetwork.support.CertificateException("Error opening node certificate", e);
		}

		setSystemKeyStoreProperties();
	}

	private void saveKeyStore(KeyStore keyStore) {
		if ( keyStore == null ) {
			return;
		}
		File ksFile = new File(keyStorePath);
		File ksDir = ksFile.getParentFile();
		if ( !ksDir.isDirectory() && !ksDir.mkdirs() ) {
			throw new RuntimeException("Unable to create KeyStore directory: " + ksFile.getParent());
		}
		OutputStream out = null;
		try {
			String passwd = getKeyStorePassword();
			out = new BufferedOutputStream(new FileOutputStream(ksFile));
			keyStore.store(out, passwd.toCharArray());
		} catch ( KeyStoreException e ) {
			throw new net.solarnetwork.support.CertificateException(
					"Error creating certificate key store", e);
		} catch ( NoSuchAlgorithmException e ) {
			throw new net.solarnetwork.support.CertificateException(
					"Error creating certificate key store", e);
		} catch ( CertificateException e ) {
			throw new net.solarnetwork.support.CertificateException(
					"Error creating certificate key store", e);
		} catch ( IOException e ) {
			throw new net.solarnetwork.support.CertificateException(
					"Error creating certificate key store", e);
		} finally {
			if ( out != null ) {
				try {
					out.flush();
					out.close();
				} catch ( IOException e ) {
					throw new net.solarnetwork.support.CertificateException(
							"Error closing KeyStore file: " + ksFile.getPath(), e);
				}
			}
		}
	}

	private KeyStore loadKeyStore() {
		File ksFile = new File(keyStorePath);
		InputStream in = null;
		KeyStore keyStore = null;
		String passwd = getKeyStorePassword();
		if ( passwd == null ) {
			log.info("Network association confirmation not available, cannot open key store");
			return null;
		}
		try {
			keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			if ( ksFile.isFile() ) {
				in = new BufferedInputStream(new FileInputStream(ksFile));
			}
			keyStore.load(in, passwd.toCharArray());
			return keyStore;
		} catch ( KeyStoreException e ) {
			throw new net.solarnetwork.support.CertificateException(
					"Error creating certificate key store", e);
		} catch ( NoSuchAlgorithmException e ) {
			throw new net.solarnetwork.support.CertificateException(
					"Error creating certificate key store", e);
		} catch ( CertificateException e ) {
			throw new net.solarnetwork.support.CertificateException(
					"Error creating certificate key store", e);
		} catch ( IOException e ) {
			throw new net.solarnetwork.support.CertificateException(
					"Error creating certificate key store", e);
		} finally {
			if ( in != null ) {
				try {
					in.close();
				} catch ( IOException e ) {
					log.warn("Error closing key store file {}: {}", ksFile.getPath(), e.getMessage());
				}
			}
		}
	}

	private String getSetting(String key) {
		return settingDao.getSetting(key, SETUP_TYPE_KEY);
	}

	public void setKeyStorePath(String keyStorePath) {
		this.keyStorePath = keyStorePath;
	}

	public void setSettingDao(SettingDao settingDao) {
		this.settingDao = settingDao;
	}

	public void setNodeAlias(String nodeAlias) {
		this.nodeAlias = nodeAlias;
	}

	public void setCaAlias(String caAlias) {
		this.caAlias = caAlias;
	}

	public void setKeySize(int keySize) {
		this.keySize = keySize;
	}

	public void setCertificateService(CertificateService certificateService) {
		this.certificateService = certificateService;
	}

	public void setManualKeyStorePassword(String manualKeyStorePassword) {
		this.manualKeyStorePassword = manualKeyStorePassword;
	}

}
