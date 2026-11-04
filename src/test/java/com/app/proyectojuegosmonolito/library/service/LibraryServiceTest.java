package com.app.proyectojuegosmonolito.library.service;

import com.app.proyectojuegosmonolito.game.service.GameService;
import com.app.proyectojuegosmonolito.library.model.Library;
import com.app.proyectojuegosmonolito.library.repository.LibraryRepository;
import com.app.proyectojuegosmonolito.account.user.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static com.app.proyectojuegosmonolito.game.GameFixtures.*;
import static com.app.proyectojuegosmonolito.library.LibraryFixtures.*;
import static com.app.proyectojuegosmonolito.account.user.UserFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryServiceTest {

    @Mock
    private LibraryRepository libraryRepository;
    @Mock
    private UserService userService;
    @Mock
    private GameService gameService;

    @InjectMocks
    private LibraryService libraryService;

    @Test
    void add_whenNotOwned_shouldAdd() {
        var user = user(1L);
        var game = game(1L);
        when(userService.findById(1L)).thenReturn(user);
        when(gameService.findById(1L)).thenReturn(game);
        when(libraryRepository.existsByUser_IdAndGame_Id(1L, 1L)).thenReturn(false);
        when(libraryRepository.save(any(Library.class))).thenAnswer(i -> i.getArgument(0));

        var result = libraryService.add(1L, 1L);

        assertThat(result.getAcquiredAt()).isNotNull();
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getGame()).isEqualTo(game);
        verify(libraryRepository).save(any(Library.class));
    }

    @Test
    void add_whenAlreadyOwned_shouldThrow() {
        when(libraryRepository.existsByUser_IdAndGame_Id(1L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> libraryService.add(1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already in library");

        verify(userService, never()).findById(any());
        verify(gameService, never()).findById(any());
        verify(libraryRepository, never()).save(any());
    }

    @Test
    void findByUserId_shouldReturnPage() {
        var pageable = PageRequest.of(0, 10);
        var libs = List.of(Library.builder().id(1L).build());
        var page = new PageImpl<>(libs, pageable, 1);
        when(libraryRepository.findByUser_IdOrderByAcquiredAtDesc(1L, pageable)).thenReturn(page);

        var result = libraryService.findByUserId(1L, pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void isGameOwned_whenOwned_shouldReturnTrue() {
        when(libraryRepository.existsByUser_IdAndGame_Id(1L, 1L)).thenReturn(true);

        assertThat(libraryService.isGameOwned(1L, 1L)).isTrue();
    }

    @Test
    void isGameOwned_whenNotOwned_shouldReturnFalse() {
        when(libraryRepository.existsByUser_IdAndGame_Id(1L, 1L)).thenReturn(false);

        assertThat(libraryService.isGameOwned(1L, 1L)).isFalse();
    }

    @Test
    void removeByGame_whenOwned_shouldDelete() {
        var user = user(1L);
        var game = game(1L);
        var lib = library(1L, user, game);
        when(libraryRepository.findByUser_IdAndGame_Id(1L, 1L)).thenReturn(Optional.of(lib));

        libraryService.removeByGame(1L, 1L);

        verify(libraryRepository).delete(lib);
    }

    @Test
    void removeByGame_whenNotFound_shouldThrow() {
        when(libraryRepository.findByUser_IdAndGame_Id(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> libraryService.removeByGame(1L, 999L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("999");

        verify(libraryRepository, never()).delete(any());
    }

    @Test
    void clear_shouldDeleteAllByUser() {
        libraryService.clear(1L);

        verify(libraryRepository).deleteByUser_Id(1L);
    }
}
