package com.ujjwal.Stream_Backend.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class SpotifyOAuthModel {
	
	@Id
	private String userId;
	
	@Column(nullable = false,length=1024)
	private String accessToken;
	
	@Column(nullable = false, length=1024)
	private String refreshToken;
	
	@Column(nullable =false)
	private String product;
	
	@Column(nullable = false)
	private Long expiresAt; // Store token expiry timestamp (System.currentTimeMillis() + expires_in * 1000)
}
