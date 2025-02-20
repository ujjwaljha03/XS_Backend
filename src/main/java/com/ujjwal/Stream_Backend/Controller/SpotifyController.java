package com.ujjwal.Stream_Backend.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ujjwal.Stream_Backend.Config.OAuthConfig;
import com.ujjwal.Stream_Backend.Service.SpotifyService;
import com.ujjwal.Stream_Backend.Model.SpotifyOAuthModel;
import com.ujjwal.Stream_Backend.Repository.SpotifyTokenRepository;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
public class SpotifyController {

	@Autowired
	private final SpotifyService spotifyService;
	@Autowired
	private SpotifyTokenRepository tokenRepository;

	public SpotifyController(OAuthConfig oAuthConfig, SpotifyService spotifyService) {
		this.spotifyService = spotifyService;
	}

	// REQUEST FOR LOGIN TO SPOTIFY
	@GetMapping("/")
	public String print() {
		return "Hello World";
	}

	@GetMapping("/spotify-login")
	public void loginSpotify(HttpServletResponse response) throws IOException {
		spotifyService.loginSpotify(response);
	}

	// AFTER LOGIN REDIRECT TO CALLBACK SPOTIFY WITH AUTH CODE

	@GetMapping("/spotify/callback")
	public void handleSpotifyCallback(HttpSession session, HttpServletResponse res, @RequestParam("code") String code,
			@RequestParam("state") String stateRecieved, @RequestParam(required = false) String error)
			throws Throwable {

		spotifyService.handleSpotifyCallback(session, res, code, stateRecieved, error);
	}

	// TO GET ACCESS TOKEN

	@GetMapping("/spotify-access-token")
	public ResponseEntity<Map<String, String>> getAccessToken(@RequestParam(value = "userId") String userId) {

		String token = spotifyService.getToken(userId);
		if (token == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
		}
		Map<String, String> response = new HashMap<>();
		response.put("token", token);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/spotify-logout")
	public ResponseEntity<?> logoutSpotify(@RequestBody Map<String, String> payload) {

		String userId = payload.get("userId"); // Extract userId from JSON request body

		if (userId == null || userId.isEmpty()) {
			return ResponseEntity.badRequest().body("User ID is required");
		}
		spotifyService.clearSpotifyAuthState(userId);
		return ResponseEntity.ok().body("Logout successful");
	}

	@GetMapping("/spotify-user")
	public ResponseEntity<?> getUserID(HttpSession session) {
		String spotifyId = (String) session.getAttribute("spotifyUserId");
		if (spotifyId == null) {

			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not logged in");
		}
		Optional<SpotifyOAuthModel> userOptional = tokenRepository.findByUserId(spotifyId);
		if (!userOptional.isPresent()) {

			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User Not Found");
		}
		SpotifyOAuthModel user = userOptional.get();
		Map<String, String> response = new HashMap<>();
		response.put("userId", user.getUserId());
		response.put("product", user.getProduct());
		return ResponseEntity.ok(response);
	}

	// CALLIN SPOTIFY API'S THROUGH ACCESS TOKEN STORED IN DATABASE

	@GetMapping("/spotify-liked-songs")
	public ResponseEntity<List<Map<String, Object>>> getAllLikedSongs(@RequestParam String spotifyUserId) {
		List<Map<String, Object>> response = spotifyService.getAllLikedSongs(spotifyUserId);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/spotify-playlists")
	public ResponseEntity<List<Map<String, Object>>> getUserPlaylists(@RequestParam String spotifyUserId) {
		List<Map<String, Object>> response = spotifyService.getUserPlaylists(spotifyUserId);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/spotify-playlist-tracks/{playlistId}")
	public ResponseEntity<List<Map<String, Object>>> getPlaylistTracks(@PathVariable String playlistId,
			@RequestParam String spotifyUserId) {
		List<Map<String, Object>> response = spotifyService.getPlaylistTracks(playlistId, spotifyUserId);
		return ResponseEntity.ok(response);
	}

	// uri is like this "spotify:album:7e5hMuXIJOrJY75lwR6hGg" or
	// spotify:track:7e5hMuXIJOrJY75lwR6hGg" , URI is tarah listed
	// hai(items[{key1:},{key2;},{track:{"uri":"spotify:track:7e5hMuXIJOrJY75lwR6hGg"}],
	// toh humain iss uri ko extract karna hai taaki track play h sake
	// PROBLME IS HUMAIN YE FRONTEND MAIN PLAY KARNA HOGA YA PHIR PLAY BUTTON PE
	// MUJHE ISS BACKEND KO CALL KARNA HOGA,(PHIR SPOTIFY WEBSDK KI KYA JARURAT)

	// REMM YE API TABHI CHALEGI JAB MERPE PREMUIM HOGA

	// This API will allow users to play specific songs from their playlists.

	@PutMapping("/spotify-play")
	public ResponseEntity<String> playSong(@RequestBody Map<String, String> payload) {
		ResponseEntity<String> response = spotifyService.playSong(payload);
		if (response.getStatusCode() == HttpStatus.OK) {
			return ResponseEntity.ok("PlayBack Started");
		}
		return ResponseEntity.badRequest().body(response.getStatusCode() + ":" + " Problem starting playback of track");
	}
}
