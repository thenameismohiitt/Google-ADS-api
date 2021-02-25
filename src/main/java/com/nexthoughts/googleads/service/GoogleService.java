package com.nexthoughts.googleads.service;

import com.google.api.ads.adwords.axis.factory.AdWordsServices;
import com.google.api.ads.adwords.lib.client.AdWordsSession;
import com.google.api.ads.adwords.lib.client.reporting.ReportingConfiguration;
import com.google.api.ads.adwords.lib.factory.AdWordsServicesInterface;
import com.google.api.ads.adwords.lib.jaxb.v201809.DownloadFormat;
import com.google.api.ads.adwords.lib.jaxb.v201809.ReportDefinitionDateRangeType;
import com.google.api.ads.adwords.lib.jaxb.v201809.ReportDefinitionReportType;
import com.google.api.ads.adwords.lib.utils.ReportDownloadResponse;
import com.google.api.ads.adwords.lib.utils.v201809.ReportDownloaderInterface;
import com.google.api.ads.adwords.lib.utils.v201809.ReportQuery;
import com.google.api.ads.common.lib.auth.GoogleClientSecretsBuilder;
import com.google.api.ads.common.lib.auth.OfflineCredentials;
import com.google.api.ads.common.lib.conf.ConfigurationLoadException;
import com.google.api.ads.common.lib.exception.ValidationException;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.nexthoughts.googleads.payload.GoogleResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.api.ads.common.lib.utils.Builder.DEFAULT_CONFIGURATION_FILENAME;
import static java.nio.charset.StandardCharsets.UTF_8;


@Service
public class GoogleService {


    public static final String ADWORDS_API_SCOPE = "https://www.googleapis.com/auth/adwords";

    private static final List<String> SCOPES = Arrays.asList(ADWORDS_API_SCOPE);

    private static final String CALLBACK_URL = "urn:ietf:wg:oauth:2.0:oob";

    private static Credential getOAuth2Credential(GoogleClientSecrets clientSecrets)
            throws IOException {
        GoogleAuthorizationCodeFlow authorizationFlow = new GoogleAuthorizationCodeFlow.Builder(
                new NetHttpTransport(),
                new JacksonFactory(),
                clientSecrets,
                SCOPES)
                .setAccessType("offline").build();

        String authorizeUrl =
                authorizationFlow.newAuthorizationUrl().setRedirectUri(CALLBACK_URL).build();
        System.out.printf("Paste this url in your browser:%n%s%n", authorizeUrl);

        System.out.println("Type the code you received here: ");
        @SuppressWarnings("DefaultCharset") // Reading from stdin, so default charset is appropriate.
                String authorizationCode = new BufferedReader(new InputStreamReader(System.in)).readLine();

        GoogleAuthorizationCodeTokenRequest tokenRequest =
                authorizationFlow.newTokenRequest(authorizationCode);
        tokenRequest.setRedirectUri(CALLBACK_URL);
        GoogleTokenResponse tokenResponse = tokenRequest.execute();

        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(new NetHttpTransport())
                .setJsonFactory(new JacksonFactory())
                .setClientSecrets(clientSecrets)
                .build();

        credential.setFromTokenResponse(tokenResponse);

        return credential;
    }

    public String getRefreshToken() {
        GoogleClientSecrets clientSecrets = null;
        try {
            clientSecrets = new GoogleClientSecretsBuilder()
                    .forApi(GoogleClientSecretsBuilder.Api.ADWORDS)
                    .fromFile()
                    .build();
        } catch (ValidationException e) {
            return "";
        } catch (ConfigurationLoadException cle) {
            System.err.printf(
                    "Failed to load configuration from the %s file. Exception: %s%n",
                    DEFAULT_CONFIGURATION_FILENAME, cle);
            return "";
        }

        // Get the OAuth2 credential.
        Credential credential = null;
        try {
            credential = getOAuth2Credential(clientSecrets);
        } catch (IOException ioe) {
            System.err.printf("Failed to generate credentials. Exception: %s%n", ioe);
            return "";
        }

        System.out.printf("Your refresh token is: %s%n", credential.getRefreshToken());

        System.out.printf("In your ads.properties file, modify:%n%napi.adwords.refreshToken=%s%n",
                credential.getRefreshToken());
        return credential.getRefreshToken();
    }


    public ResponseEntity<?> getStats() {
        AdWordsSession session;
        try {
            Credential oAuth2Credential = new OfflineCredentials.Builder()
                    .forApi(OfflineCredentials.Api.ADWORDS)
                    .fromFile()
                    .build()
                    .generateCredential();
            session = new AdWordsSession.Builder().fromFile().withOAuth2Credential(oAuth2Credential).build();
            String reportFile = System.getProperty("user.home") + File.separatorChar + "report.csv";
            AdWordsServicesInterface adWordsServices = AdWordsServices.getInstance();
            ReportQuery query = new ReportQuery.Builder()
                    .fields("Id", "Impressions", "Clicks", "AverageCpm", "CampaignId", "CampaignName")
                    .from(ReportDefinitionReportType.AD_PERFORMANCE_REPORT)
                    .where("Status").in("ENABLED", "PAUSED")
                    .during(ReportDefinitionDateRangeType.TODAY)
                    .build();

            ReportingConfiguration reportingConfiguration = new ReportingConfiguration.Builder()
                    .skipReportHeader(true)
                    .skipColumnHeader(true)
                    .skipReportSummary(true)
                    .includeZeroImpressions(true)
                    .build();
            session.setReportingConfiguration(reportingConfiguration);
            ReportDownloaderInterface reportDownloader = adWordsServices.getUtility(session, ReportDownloaderInterface.class);
            ReportDownloadResponse response = reportDownloader.downloadReport(query.toString(), DownloadFormat.CSV);
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.getInputStream(), UTF_8));
            List<GoogleResponse> googleResponses = createJSON(reader);
            return new ResponseEntity<>(googleResponses, HttpStatus.OK);
        } catch (Exception exception) {
            System.out.println(exception);
            return new ResponseEntity<>(exception, HttpStatus.CONFLICT);
        }
    }

    public List<GoogleResponse> createJSON(BufferedReader reader) throws IOException {
        String line = "";
        List<GoogleResponse> googleResponses = new ArrayList<>();
        while ((line = reader.readLine()) != null) {
            GoogleResponse googleResponse = new GoogleResponse();
            String newLine = line.replaceAll("[\\[\\]]", "");
            newLine = newLine.replaceAll("\"\"\"", "");

            List<String> values = Arrays.asList(newLine.split(","));

            String adId = values.get(0);
            googleResponse.setAdId(adId);

            Long impressions = Long.parseLong(values.get(1));
            googleResponse.setImpressions(impressions);

            Long clicks = Long.parseLong(values.get(2));
            googleResponse.setClicks(clicks);

            Long avgCpm = Long.parseLong(values.get(3));
            googleResponse.setAvgCpm(avgCpm);

            String campaignId = values.get(4);
            googleResponse.setCampaignId(campaignId);

            String campaignName = values.get(5);
            googleResponse.setCampaignName(campaignName);

            googleResponses.add(googleResponse);

        }
        return googleResponses;

    }
}
