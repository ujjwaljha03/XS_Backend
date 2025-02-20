package com.ujjwal.Stream_Backend.Controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ujjwal.Stream_Backend.Model.SoundcloudOAuthModel;
import com.ujjwal.Stream_Backend.Repository.SoundcloudTokenRepository;
import com.ujjwal.Stream_Backend.Service.SoundcloudService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@RestController
public class SoundcloudController {

	@Autowired
	private final SoundcloudService scService;
	@Autowired
	private SoundcloudTokenRepository tokenRepository;

	public SoundcloudController(SoundcloudService scService) {
		this.scService = scService;
	}

	// REQUEST FOR LOGIN TO SOUNDCLOUD

	@GetMapping("/auth/soundcloud/login")
	public void loginSoundcloud(HttpServletResponse response) throws Exception {
		scService.loginSoundCloud(response);
	}

	// AFTER LOGIN REDIRECT TO CALLBACK SOUNDCLOUD WITH AUTH CODE

	@GetMapping("/auth/soundcloud/callback")
	public void handleSoundCloudCallback(HttpSession session,HttpServletResponse res, @RequestParam("code") String code,
			@RequestParam(value = "state", required = false) String stateRecieved,
			@RequestParam(value = "error", required = false) String error) throws Throwable {

		scService.handleSoundCloudCallback(session,res, code, stateRecieved, error);

	}
	
	@PostMapping("/soundcloud/logout")
	public ResponseEntity<?> logoutSoundcloud(HttpServletRequest request) {
	    HttpSession session = request.getSession(false);
	    if (session != null) {
	        // Get the user ID from the session
	        Integer userId = (Integer) session.getAttribute("soundCloudUserId");
	        
	        if (userId != null) {
	            // Clear or update the user's authentication state in the database
	            scService.clearSoundcloudAuthState(userId);
	        }
	        
	        // Invalidate the session
	        session.invalidate();
	    }
	    return ResponseEntity.ok().build();
	}
	
	@GetMapping("/soundcloud/user")
	public ResponseEntity<?> getUserID(HttpSession session) {
		Integer soundcloudId = (Integer) session.getAttribute("soundcloudUserId");
		if(soundcloudId==null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("User not logged in");
		}
		Optional<SoundcloudOAuthModel> userOptional = tokenRepository.findByUserId(soundcloudId);
		if(!userOptional.isPresent()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User Not Found");
		}
		SoundcloudOAuthModel user = userOptional.get();
		return ResponseEntity.ok(user.getUserId());
	}
	
	// API CAL FOR SOUNDCLOUD LIKED SONGS

	@GetMapping("/soundcloud-liked-songs")
	public ResponseEntity<List<Map<String, Object>>> getAllLikedSongs(@RequestParam Integer soundcloudUserId) { 
		
		ResponseEntity<List<Map<String, Object>>> response = scService.getAllLikedSongs(soundcloudUserId);
		if (response.getStatusCode() == HttpStatus.OK) {
			return ResponseEntity.ok(response.getBody());
		}
		return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
	}

	@GetMapping("/soundcloud-playlists")
	public ResponseEntity<List<Map<String, Object>>> getUserPlaylists(@RequestParam Integer soundcloudUserId) {
		ResponseEntity<List<Map<String, Object>>> response = scService.getUserPlaylists(soundcloudUserId);
		if (response.getStatusCode() == HttpStatus.OK) {
			return ResponseEntity.ok(response.getBody());
		}
		return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
	}

	// **** RIGHT NOW THIS API IS NOT MADE PUBLIC BY SOUNDCLOUD SO NOT IN USE FOR US

	@GetMapping("/soundcloud-recently-played")
	public ResponseEntity<List<Map<String, Object>>> getUserRecentlyPlayedTracks() {
		ResponseEntity<List<Map<String, Object>>> response = scService.getUserRecentlyPlayedTracks();
		if (response.getStatusCode() == HttpStatus.OK) {
			return ResponseEntity.ok(response.getBody());
		}
		return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
	}
}
