package com.ujjwal.Stream_Backend.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.ujjwal.Stream_Backend.Config.OAuthConfig;
import com.ujjwal.Stream_Backend.Model.SpotifyOAuthModel;
import com.ujjwal.Stream_Backend.Repository.SpotifyTokenRepository;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.util.Map;
import java.util.Optional;

@Service
public class SpotifyService {
	@Autowired
	private final OAuthConfig oAuthConfig;

	@Autowired
	private final SpotifyTokenRepository tokenRepository;
	private final RestTemplate restTemplate;

	public SpotifyService(OAuthConfig oAuthConfig, SpotifyTokenRepository tokenRepository) {
		this.oAuthConfig = oAuthConfig;
		this.restTemplate = new RestTemplate();
		this.tokenRepository = tokenRepository;
	}

	// REQUEST FOR LOGIN TO SPOTIFY

	private String stateSend;

	public void loginSpotify(HttpServletResponse response) throws IOException {
		stateSend = UUID.randomUUID().toString(); // CSRF PROTECTION
		String authorizationUrl = oAuthConfig.getSpotifyAuthUrl() + "?response_type=code" + "&client_id="
				+ oAuthConfig.getSpotifyClientId() + "&scope=" + oAuthConfig.getSpotifyScope().replace(" ", "%20")
				+ "&redirect_uri=" + oAuthConfig.getSpotifyRedirectUri() + "&state=" + stateSend;

		// Redirecting to Spotify
		response.sendRedirect(authorizationUrl);

	}

	// AFTER LOGIN REDIRECT TO CALLBACK SPOTIFY WITH AUTH CODE

	public void handleSpotifyCallback(HttpSession session, HttpServletResponse res, String code, String stateRecieved,
			String error) throws Throwable {
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
			String id_key = oAuthConfig.getSpotifyClientId() + ":" + oAuthConfig.getSpotifyClientSecret();
			String credentials = Base64.getEncoder().encodeToString(id_key.getBytes());
			headers.setBasicAuth(credentials);

			MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
			params.add("grant_type", "authorization_code");
			params.add("code", code);
			params.add("redirect_uri", oAuthConfig.getSpotifyRedirectUri());

			HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(oAuthConfig.getSpotifyTokenUrl(),
					HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {
					}); // Earlier used restTemplate.postforEntity

			if (response.getStatusCode().is2xxSuccessful()) {

				String accessToken = (String) response.getBody().get("access_token");
				String refreshToken = (String) response.getBody().get("refresh_token");
				Integer expiresIn = (Integer) response.getBody().get("expires_in");
				SpotifyOAuthModel tokenModel = new SpotifyOAuthModel();
				tokenModel.setAccessToken(accessToken);
				tokenModel.setRefreshToken(refreshToken);
				tokenModel.setExpiresAt(Instant.now().getEpochSecond() + expiresIn);
				Map<String, String> userIdAndProduct = getUserInfo(accessToken, tokenModel);

				// STORING IN SESSION , BCAUZ FORNTEND NEEDS USERID AFTER LOGGIN FOR THE SAME
				// USER ONLY
				session.setAttribute("spotifyUserId", userIdAndProduct.get("userId"));

				tokenModel.setUserId(userIdAndProduct.get("userId"));
				tokenModel.setProduct(userIdAndProduct.get("product"));
				tokenRepository.save(tokenModel);

				res.sendRedirect("http://localhost:3000/"); // NEED TO CHANGE WITH REAL FRONTEND URL WHEN DEPLOYED
			}

		} catch (Exception e) {

			e.printStackTrace();
			res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error generating login URL");
		}
	}

	private Map<String, String> getUserInfo(String accessToken, SpotifyOAuthModel tokenModel) {
		String userIdUrl = "https://api.spotify.com/v1/me";
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);

		HttpEntity<String> request = new HttpEntity<>(headers);
		ResponseEntity<Map<String, Object>> response = restTemplate.exchange(userIdUrl, HttpMethod.GET, request,
				new ParameterizedTypeReference<Map<String, Object>>() {
				});

		if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
			String userId = (String) response.getBody().get("id");
			String product = (String) response.getBody().get("product");
			Map<String, String> map = new HashMap<>();
			map.put("userId", userId);
			map.put("product", product);
			return map;
		} else {
			throw new RuntimeException(
					"Error: " + response.getStatusCode() + " : Failed to fetch Spotify user profile");
		}

	}

	public String getAccessTokenFunction(String userId) {
		Optional<SpotifyOAuthModel> model = tokenRepository.findByUserId(userId);// .orElseThrow(() -> new
																					// RuntimeException("User not
																					// found"));
		if (model.isPresent()) {
			SpotifyOAuthModel token = model.get();
			if (Instant.now().getEpochSecond() >= token.getExpiresAt()) {
				return refreshAccessToken(userId);
			}
			return token.getAccessToken();
		} else {
			return null;
		}
	}

	public String refreshAccessToken(String userId) {
		SpotifyOAuthModel token = tokenRepository.findByUserId(userId)
				.orElseThrow(() -> new RuntimeException("User not found"));

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.setBasicAuth(oAuthConfig.getSpotifyClientId(), oAuthConfig.getSpotifyClientSecret());

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("grant_type", "refresh_token");
		params.add("refresh_token", token.getRefreshToken());

		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
		ResponseEntity<Map<String, Object>> response = restTemplate.exchange(oAuthConfig.getSpotifyTokenUrl(),
				HttpMethod.POST, request, new ParameterizedTypeReference<Map<String, Object>>() {
				});

		if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
			String newAccessToken = (String) response.getBody().get("access_token");
			String newRefreshToken = (String) response.getBody().getOrDefault("refresh_token", token.getRefreshToken());
			Integer newExpiresIn = (Integer) response.getBody().get("expires_in");

			token.setAccessToken(newAccessToken);
			token.setRefreshToken(newRefreshToken);
			token.setExpiresAt(Instant.now().getEpochSecond() + newExpiresIn);

			tokenRepository.save(token);
			return newAccessToken;
		}

		throw new RuntimeException("Failed to refresh Spotify token");
	}

	// API CALL TO GET ALL LIKED SONGS FROM USER

	public List<Map<String, Object>> getAllLikedSongs(String spotifyUserId) {

		int limit = 50;
		int offset = 0;
		String likedSongsUrl = "https://api.spotify.com/v1/me/tracks?limit=" + limit + "&offset=" + offset;
		String getNextLikedSongsUrl = "$";

		String accessToken = getAccessTokenFunction(spotifyUserId);

		List<Map<String, Object>> allTracks = new ArrayList<>();
		while (getNextLikedSongsUrl != null) {
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth(accessToken);
			HttpEntity<String> request = new HttpEntity<>(headers);
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(likedSongsUrl, HttpMethod.GET, request,
					new ParameterizedTypeReference<Map<String, Object>>() {
					});

			List<Map<String, Object>> tracks = (List<Map<String, Object>>) response.getBody().get("items");
			allTracks.addAll(tracks);
			offset += limit;
			likedSongsUrl = "https://api.spotify.com/v1/me/tracks?limit=" + limit + "&offset=" + offset;
			getNextLikedSongsUrl = (String) response.getBody().get("next");
		}

		return allTracks;
	}

	public List<Map<String, Object>> getUserPlaylists(String spotifyUserId) {

		String accessToken = getAccessTokenFunction(spotifyUserId);
		int limit = 50;
		int offset = 0;
		String playlistUrl = "https://api.spotify.com/v1/me/playlists?limit=" + limit + "&offset=" + offset;
		String nextUrl = "$";
		List<Map<String, Object>> allPlaylists = new ArrayList<>();
		while (nextUrl != null) {
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth(accessToken);
			HttpEntity<String> request = new HttpEntity<>(headers);
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(playlistUrl, HttpMethod.GET, request,
					new ParameterizedTypeReference<Map<String, Object>>() {
					});
			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				List<Map<String, Object>> playlists = (List<Map<String, Object>>) response.getBody().get("items");
				allPlaylists.addAll(playlists);
				offset += limit;
				playlistUrl = "https://api.spotify.com/v1/me/playlists?limit=" + limit + "&offset=" + offset;
				nextUrl = (String) response.getBody().get("next");
			}
		}
		return allPlaylists;
	}

	public List<Map<String, Object>> getPlaylistTracks(String playlistId, String spotifyUserId) {

		String accessToken = getAccessTokenFunction(spotifyUserId);
		int limit = 100;
		int offset = 0;
		String playlistTracksUrl = "https://api.spotify.com/v1/playlists/" + playlistId + "/tracks?offset=" + offset
				+ "&limit=" + limit;
		String nextUrl = "$";
		List<Map<String, Object>> allPlaylistTracks = new ArrayList<>();
		while (nextUrl != null) {
			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth(accessToken);
			HttpEntity<String> request = new HttpEntity<>(headers);
			ResponseEntity<Map<String, Object>> response = restTemplate.exchange(playlistTracksUrl, HttpMethod.GET,
					request, new ParameterizedTypeReference<Map<String, Object>>() {
					});
			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				List<Map<String, Object>> playlistTracks = (List<Map<String, Object>>) response.getBody().get("items");
				allPlaylistTracks.addAll(playlistTracks);
				offset += limit;
				playlistTracksUrl = "https://api.spotify.com/v1/playlists/" + playlistId + "/tracks?offset=" + offset
						+ "&limit=" + limit;
				nextUrl = (String) response.getBody().get("next");
			}
		}
		return allPlaylistTracks;
	}

	public ResponseEntity<String> playSong(Map<String, String> payload) {

		String deviceId = payload.get("deviceId");
		String uri = payload.get("uri");
		String userId = payload.get("userId");
		String accessToken = getAccessTokenFunction(userId);
		if (accessToken == null) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("user not found");
		}

		if (deviceId == null || uri == null) {
			return ResponseEntity.badRequest().body("Missing deviceId or uri");
		}

		String playTrackUrl = "https://api.spotify.com/v1/me/player/play?device_id=" + deviceId;

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		headers.setContentType(MediaType.APPLICATION_JSON);

		// Correct JSON body structure
		Map<String, Object> requestBody = new HashMap<>();
		requestBody.put("uris", Collections.singletonList(uri));

		HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

		try {
			ResponseEntity<String> response = restTemplate.exchange(playTrackUrl, HttpMethod.PUT, request,
					String.class);
			if (response.getStatusCode() == HttpStatus.NO_CONTENT) {
				return ResponseEntity.ok("Playback started");
			}
			return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
		} catch (HttpClientErrorException e) {
			return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
		}
	}

	public void clearSpotifyAuthState(String userId) {
		Optional<SpotifyOAuthModel> repo = tokenRepository.findByUserId(userId);
		if (repo.isPresent()) {
			SpotifyOAuthModel user = repo.get();
			tokenRepository.delete(user);
		}

	}

	// For Frontend Spotify Player Initilization
	public String getToken(String userId) {
		Optional<SpotifyOAuthModel> model = tokenRepository.findByUserId(userId);// .orElseThrow(() -> new
																					// RuntimeException("User not
																					// found"));
		if (model.isPresent()) {
			SpotifyOAuthModel token = model.get();
			if (Instant.now().getEpochSecond() >= token.getExpiresAt()) {
				return refreshAccessToken(userId);
			}
			return token.getAccessToken();
		} else {
			return null;
		}
	}

}
