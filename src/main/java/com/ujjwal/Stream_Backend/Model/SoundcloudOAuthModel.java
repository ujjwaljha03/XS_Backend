package com.ujjwal.Stream_Backend.Model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class SoundcloudOAuthModel {
	
	@Id
    private Integer userId;
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private String scope;
}
