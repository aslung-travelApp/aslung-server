package com.trip.aslung.user.model.service;

import com.trip.aslung.user.model.dto.User;

public interface UserService {
    User getUserByEmail(String email);
}
