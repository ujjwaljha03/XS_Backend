package com.ujjwal.Stream_Backend.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ujjwal.Stream_Backend.Config.OAuthConfig;
import com.ujjwal.Stream_Backend.Model.SoundcloudOAuthModel;
import com.ujjwal.Stream_Backend.Repository.SoundcloudTokenRepository;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Service
public class SoundcloudService {

	@Autowired
	private final OAuthConfig oAuthConfig;
	@Autowired
	private final SoundcloudTokenRepository tokenRepository;
	private final RestTemplate restTemplate;
	private HttpSession session;

	public SoundcloudService(OAuthConfig oAuthConfig, SoundcloudTokenRepository tokenRepository, HttpSession session) {
		this.oAuthConfig = oAuthConfig;
		this.restTemplate = new RestTemplate();
		this.tokenRepository = tokenRepository;
		this.session = session;
	}

	// THESE TWO METHODS ARE USED BEACUSE SOUNDCLOUD WORKS WITH PKCE("PIXY") CODE
	// METHOD
	// FIRST: AT CLIENT SIDE FIRST WE GENERATE A CODE VERIFIER THEN , WE GENERATE
	// CODE CHALLENGE THROUGH HASHING METHOID(SHA-256 USED HERE) USING CODE VERIFIER
	// SECOND: WE SEND THE CODE CHALLENGE AND HASHING METHOD TYPE WE USED(S256 HERE)
	// TO GET THE AUTHORIZATION CODE
	// THIRD: TO RECIEVE ACCESS TOKEN AFTER AUTHORIZATION, WE WILL SEND CODE
	// VERIFIER SO THAT SOUNDCLOUUD AT HERI SIDE AFTER REDUCING
	// CODECHALLNEG WITH SAME HASHINGMETHOD GETS SAME STRING THAT WE SENT THROUGH
	// CODE VERIFIER.
	// THIS WAY VERIFICATION AND PROTECTION AGINST ATTACK IS DONE.

	public String generateCodeVerifier() {
		try {
			// the code_verifier is a random, Cryptographically secure string.
			// It must be between 43 and 128 characters long.

			byte[] randomBytes = new byte[32]; // 32 bytes = 256 bits
			SecureRandom secureRandom = new SecureRandom();
			secureRandom.nextBytes(randomBytes);

			// Debugging output to check if randomBytes are generated correctly
			// System.out.println("Random Bytes: " + new String(randomBytes));

			return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes); // URL-safe Base64
		} catch (Exception e) {
			// Handle any potential exceptions
			e.printStackTrace();
			return null;
		}
	}

	private String generateCodeChallenge(String codeVerifier) throws Exception {
		if (codeVerifier == null || codeVerifier.isEmpty()) {
			throw new IllegalArgumentException("Code Verifier cannot be null or empty");
		}

		// Compute SHA-256 hash of the code_verifier
		byte[] hash = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.UTF_8));

		// Return the Base64 URL-safe encoded hash
		String codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

		// Debugging output
		// System.out.println("Code Verifier: " + codeVerifier);
		// System.out.println("Code Challenge: " + codeChallenge);

		return codeChallenge;
	}

	// HtppSession is like a local storage, i can store data in session until user
	// log's out or session is OVER

	// LOGIN IN SOUNDCLOUD

	public void loginSoundCloud(HttpServletResponse response) throws Exception {
		try {
			String codeVerifier = generateCodeVerifier();
			String codeChallenge = generateCodeChallenge(codeVerifier);
			String stateSend = UUID.randomUUID().toString();
			session.setAttribute("code_verifier", codeVerifier);
			session.setAttribute("state", stateSend); // CSRF state storage

			// Build authorization URL
			String authorizationUrl = oAuthConfig.getSoundcloudAuthUrl() + "?client_id="
					+ oAuthConfig.getSoundCloudClientId() + "&redirect_uri=" + oAuthConfig.getSoundCloudRedirectUri()
					+ "&response_type=code" + "&code_challenge=" + codeChallenge + "&code_challenge_method=S256"
					+ "&state=" + stateSend;

			// Redirecting to SoundCloud
			response.sendRedirect(authorizationUrl);

		} catch (Exception e) {
			e.printStackTrace();
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error generating login URL");
		}
	}

	// HANDLING CALLBACK TO GET TOKEN FROM SENDING AUTH CODE

	public void handleSoundCloudCallback(HttpSession session, HttpServletResponse res,
			@RequestParam("code") String code, @RequestParam(value = "state", required = false) String stateRecieved,
			@RequestParam(value = "error", required = false) String error) throws Throwable {

		// String stateSend = (String) session.getAttribute("state");
		// if (stateRecieved == null || !stateRecieved.equals(stateSend)) {
		// return ResponseEntity.badRequest().body("State parameter mismatch! Possible
		// CSRF attack.");}

		String codeVerifier = (String) session.getAttribute("code_verifier");
		try {

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
			headers.add("accept", "application/json; charset=utf-8");

			MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
			requestBody.add("grant_type", "authorization_code");
			requestBody.add("client_id", oAuthConfig.getSoundCloudClientId());
			requestBody.add("client_secret", oAuthConfig.getSoundCloudClientSecret());
			requestBody.add("redirect_uri", oAuthConfig.getSoundCloudRedirectUri());
			requestBody.add("code_verifier", codeVerifier);
			requestBody.add("code", code);

			HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);

			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(oAuthConfig.getSoundCloudTokenUrl(),
					HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {
					});

			if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {

				Map<String, Object> responseBody = response.getBody();
				String accessToken = (String) responseBody.get("access_token");
				String refreshToken = (String) responseBody.get("refresh_token");
				int expiresIn = (Integer) responseBody.get("expires_in");
				String scope = (String) responseBody.get("scope");

//				if (accessToken == null || refreshToken == null) {
//					return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//							.body("Error: Missing token details in response.");
//				}

				SoundcloudOAuthModel tokenModel = new SoundcloudOAuthModel();
				tokenModel.setAccessToken(accessToken);
				tokenModel.setRefreshToken(refreshToken);
				tokenModel.setScope(scope);
				tokenModel.setExpiresIn(Instant.now().getEpochSecond() + expiresIn);
//				tokenModel.setExpiresIn((Long) System.currentTimeMillis() + (expiresIn * 1000L));
				Integer userId = getUserId(accessToken);

				// Storing in session for first call without user id.
				session.setAttribute("soundcloudUserId", userId);

				tokenModel.setUserId((Integer) userId);
				tokenRepository.save(tokenModel);

				// AFTER OAUTH LOGIN REDIRECTING TO FORNTEND PAGE
				res.sendRedirect("http://localhost:3000/"); // NEED TO CHANGE WITH REAL FRONTEND URL WHEN DEPLOYED
			}

			// else {
			// Handle non-OK responses
			// return ResponseEntity.status(response.getStatusCode())
			// .body("Failed to retrieve access token. Error code: " +
			// response.getStatusCode());}

		} catch (Exception e) {
			e.printStackTrace();
			res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error generating login URL");
		}
	}

	// GETTING USER ID TO STORE IT IN SAME TOKEN RECORD,AS OBTAINED ABOVE

	private Integer getUserId(String accessToken) {

		String userIdUrl = "https://api.soundcloud.com/me";
		HttpHeaders header = new HttpHeaders();
		header.add("accept", "application/json; charset=utf-8");
		header.set("Authorization", "OAuth " + accessToken);
		HttpEntity<String> request = new HttpEntity<String>(header);
		ResponseEntity<Map<String, Object>> response = restTemplate.exchange(userIdUrl, HttpMethod.GET, request,
				new ParameterizedTypeReference<Map<String, Object>>() {
				});
		if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
			return (Integer) response.getBody().get("id");
		} else {
			throw new RuntimeException(
					"Error: " + response.getStatusCode() + " : Failed to fetch Soundcloud user profile");
		}
	}

	// TOKEN REFRESHING LOGIC, USER-> CALLS API-> API CHECKS THROUGH FUNCTION IF
	// TOKEN IS EXPIRED-> CALL REFRESHTOKEN LOGIC OR NOT->GIVE ACCESTOKEN FOR GIVEN
	// USER.

	private String getValidAccessToken(Integer userId) {
		SoundcloudOAuthModel token = tokenRepository.findByUserId(userId)
				.orElseThrow(() -> new RuntimeException("User not found!"));
		if (token.getAccessToken() == null) {
			throw new RuntimeException("No access token found. Authenticate first.");
		}
		if (Instant.now().getEpochSecond() >= token.getExpiresIn()) {
			return refreshAccessToken(token);
		}
		return token.getAccessToken();
	}

	private String refreshAccessToken(SoundcloudOAuthModel token) {

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.add("accept", "application/json; charset=utf-8");

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("grant_type", "refresh_token");
		params.add("client_id", oAuthConfig.getSoundCloudClientId());
		params.add("client_secret", oAuthConfig.getSoundCloudClientSecret());
		params.add("refresh_token", token.getRefreshToken());

		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

		ResponseEntity<Map<String, Object>> response = restTemplate.exchange(oAuthConfig.getSoundCloudTokenUrl(),
				HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {
				});

		if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {

			Map<String, Object> responseBody = response.getBody();
			String newAccessToken = (String) responseBody.get("access_token");
			String newRefreshToken = (String) responseBody.get("refresh_token");
			int newExpiresIn = (Integer) responseBody.get("expires_in");
			String scope = (String) responseBody.get("scope");
			if (newAccessToken == null || newRefreshToken == null) {
				throw new RuntimeException("Error: Missing token details in response.");
			}

			// Update the existing token in the database
			token.setAccessToken(newAccessToken);
			token.setRefreshToken(newRefreshToken);
			token.setScope(scope);
			token.setExpiresIn(Instant.now().getEpochSecond() + newExpiresIn);
			tokenRepository.save(token);

			return newAccessToken;
		} else {
			throw new RuntimeException("Failed to refresh token");
		}
	}

	// ACCESS ALL LIKED SONGS FROM SOUNDCLOUD OFF A USER

	public ResponseEntity<List<Map<String, Object>>> getAllLikedSongs(Integer soundcloudUserId) {

		String accessToken = getValidAccessToken(soundcloudUserId);
		String likedSongsUrl = "https://api.soundcloud.com/me/likes/tracks?access=playable&linked_partitioning=true"; // max
																														// limit=200
		List<Map<String, Object>> allTracks = new ArrayList<>();
		int maxTracks = 1000; // Limit the number of tracks to prevent OOM
		int totalFetched = 0;
		int maxPages = 10; // Avoid infinite loops
		int currentPage = 0;

		while (likedSongsUrl != null && totalFetched < maxTracks && currentPage < maxPages) {
			HttpHeaders headers = new HttpHeaders();
			headers.set("Authorization", "OAuth " + accessToken);
			headers.add("Accept", "application/json; charset=utf-8");

			HttpEntity<String> request = new HttpEntity<>(headers);
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(likedSongsUrl, HttpMethod.GET, request,
					new ParameterizedTypeReference<Map<String, Object>>() {
					});

			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				List<Map<String, Object>> tracks = (List<Map<String, Object>>) response.getBody().get("collection");

				if (tracks != null) {
					allTracks.addAll(tracks);
					totalFetched += tracks.size();

					if (totalFetched >= maxTracks) {
						break;
					}
				}

				String nextHref = (String) response.getBody().get("next_href");
				currentPage++;

				if (nextHref != null) {
					nextHref = nextHref.replace("http.api.prod.api-public.srv.db.s-cloud.net", "api.soundcloud.com");
					// Extract cursor manually
					String cursor = extractCursor(nextHref);
					if (cursor != null) {
						likedSongsUrl = "https://api.soundcloud.com/me/likes/tracks?access=playable&linked_partitioning=true&cursor="
								+ cursor;
					} else {
						likedSongsUrl = null;
					}
					try {
						Thread.sleep(500); // Delay to avoid hitting API rate limits
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				} else {
					likedSongsUrl = null;
				}
			} else {
				likedSongsUrl = null; // Stop if response is invalid
			}
		}

		return ResponseEntity.ok(allTracks);
	}

	// Extract cursor from next_href URL
	private String extractCursor(String nextHref) {
		try {
			// Extract the cursor from the query parameters
			URI uri = new URI(nextHref);
			String query = uri.getQuery();
			for (String param : query.split("&")) {
				if (param.startsWith("cursor=")) {
					return param.substring(7); // Get the value after "cursor="
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public ResponseEntity<List<Map<String, Object>>> getUserPlaylists(Integer soundcloudUserId) {

		String accessToken = getValidAccessToken(soundcloudUserId);
		String playlistUrl = "https://api.soundcloud.com/me/playlists?show_tracks=true&linked_partitioning=true&limit=120";
		List<Map<String, Object>> allPlayLists = new ArrayList<>();

		int maxPlaylists = 1000; // Limit to prevent OOM
		int totalFetched = 0;
		int maxPages = 10; // Avoid infinite loops
		int currentPage = 0;

		while (playlistUrl != null && totalFetched < maxPlaylists && currentPage < maxPages) {

			HttpHeaders headers = new HttpHeaders();
			headers.set("Authorization", "OAuth " + accessToken);
			headers.add("Accept", "application/json; charset=utf-8");

			HttpEntity<String> request = new HttpEntity<>(headers);
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(playlistUrl, HttpMethod.GET, request,
					new ParameterizedTypeReference<Map<String, Object>>() {
					});

			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				List<Map<String, Object>> playlists = (List<Map<String, Object>>) response.getBody().get("collection");

				if (playlists != null) {
					allPlayLists.addAll(playlists);
					totalFetched += playlists.size();

					if (totalFetched >= maxPlaylists) {
						break;
					}
				}

				String nextHref = (String) response.getBody().get("next_href");
				currentPage++;

				if (nextHref != null) {
					nextHref = nextHref.replace("http.api.prod.api-public.srv.db.s-cloud.net", "api.soundcloud.com");
					// Extract cursor manually
					String cursor = extractCursor(nextHref);
					if (cursor != null) {
						playlistUrl = "https://api.soundcloud.com/me/playlists?show_tracks=true&linked_partitioning=true&limit=120&cursor="
								+ cursor;
					} else {
						playlistUrl = null;
					}
					try {
						Thread.sleep(500); // Delay to avoid hitting API rate limits
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				} else {
					playlistUrl = null;
				}
			} else {
				playlistUrl = null; // Stop if response is invalid
			}
		}

		return ResponseEntity.ok(allPlayLists);
	}

	public ResponseEntity<List<Map<String, Object>>> getUserRecentlyPlayedTracks() {
		List<SoundcloudOAuthModel> tokenDetails = tokenRepository.findAll();
		Integer userId = tokenDetails.get(0).getUserId();
		String accessToken = getValidAccessToken(userId);
		String recentTracksUrl = "https://api.soundcloud.com/me/play-history/tracks";
		List<Map<String, Object>> allRecentTracks = new ArrayList<>();
		while (recentTracksUrl != null) {
			HttpHeaders headers = new HttpHeaders();
			headers.set("Authorization", "OAuth " + accessToken);
			headers.add("accept", "application/json; charset=utf-8");

			HttpEntity<String> request = new HttpEntity<>(headers);
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(recentTracksUrl, HttpMethod.GET,
					request, new ParameterizedTypeReference<Map<String, Object>>() {
					});
			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				ObjectMapper objectMapper = new ObjectMapper();
				List<Map<String, Object>> recentTracks = objectMapper.convertValue(response.getBody().get("collection"),
						new TypeReference<List<Map<String, Object>>>() {
						});
				allRecentTracks.addAll(recentTracks);
				recentTracksUrl = (String) response.getBody().get("next_href");
			} else {
				recentTracksUrl = null; // Stop if response body is empty
			}
		}
		return ResponseEntity.ok(allRecentTracks);
	}

	public void clearSoundcloudAuthState(Integer userId) {
		Optional<SoundcloudOAuthModel> repo = tokenRepository.findByUserId(userId);
		if (repo != null) {
			SoundcloudOAuthModel user = repo.get();
			user.setAccessToken(null);
			user.setRefreshToken(null);
			user.setExpiresIn(null);
			user.setUserId(null);
			tokenRepository.save(user);
		}

	}
}
