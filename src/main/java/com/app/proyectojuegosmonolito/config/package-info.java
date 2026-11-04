@ApplicationModule(allowedDependencies = {"game :: service", "game :: model", "account :: user-service", "account :: user-model", "account :: wallet-service", "account :: profile-model", "account :: profile-repository", "blog :: service", "blog :: model"})
package com.app.proyectojuegosmonolito.config;

import org.springframework.modulith.ApplicationModule;
