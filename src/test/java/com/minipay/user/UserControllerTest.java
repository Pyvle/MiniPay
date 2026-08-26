package com.minipay.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;
    
    @Test
    void createUserShouldReturnCreatedUser() throws Exception {
        User user = new User("John Doe", "john.doe@example.com");

        when(userService.createUser("John Doe", "john.doe@example.com"))
            .thenReturn(user);
        
        mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "name": "John Doe",
                        "email": "john.doe@example.com"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("John Doe"))
            .andExpect(jsonPath("$.email").value("john.doe@example.com"));
        
        verify(userService).createUser("John Doe", "john.doe@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "     "})
    void createUserShouldReturnBadRequestWhenNameIsBlank(String name) throws Exception {
        mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "name": "%s",
                        "email": "john.doe@example.com"}
                    """.formatted(name)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists());
        
        verifyNoInteractions(userService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", 
        "  ",
        "wrong-email",
        "ivan@", 
        "@example.com", 
        "ivan example@example.com", 
        "ivan@example..com"})
    void createUserShouldReturnBadRequestWhenEmailIsInvalid(String email) throws Exception {
        mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "name": "John Doe",
                        "email": "%s"}
                    """.formatted(email)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists());

        verifyNoInteractions(userService);
    }

    @Test
    void createUserShouldReturnBadRequestAndMessageWhenUserAlreadyExists() throws Exception {
        
        when(userService.createUser("John Doe", "john.doe@example.com"))
            .thenThrow(new IllegalArgumentException(
                "User with this email already exists"));
        
        mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "name": "John Doe",
                        "email": "john.doe@example.com"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                .value("User with this email already exists"));
        
        verify(userService).createUser("John Doe", "john.doe@example.com");
    }

    @Test
    void getUserByIdShouldReturnUser() throws Exception {
        User user = new User("John Doe", "john.doe@example.com");

        when(userService.getUserById(1L))
            .thenReturn(user);
        
        mockMvc.perform(get("/api/users/{id}", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("John Doe"))
            .andExpect(jsonPath("$.email").value("john.doe@example.com"));
        
        verify(userService).getUserById(1L);
    }

    @Test
    void getUserByIdShouldReturnBadRequestWhenUserNotFound() throws Exception {
        
        when(userService.getUserById(999L))
            .thenThrow(new IllegalArgumentException("User not found"));

        mockMvc.perform(get("/api/users/{id}", 999L))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("User not found"));
        
        verify(userService).getUserById(999L);   
    }

    @Test
    void getAllUsersShouldReturnUsers() throws Exception{
        User user = new User("John Doe", "john.doe@example.com");
        User user2 = new User("Dama", "dama@example.com");

        List<User> users = List.of(user, user2);

        when(userService.getAllUsers())
            .thenReturn(users);
        
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("John Doe"))
            .andExpect(jsonPath("$[0].email").value("john.doe@example.com"))
            .andExpect(jsonPath("$[1].name").value("Dama"))
            .andExpect(jsonPath("$[1].email").value("dama@example.com"));

        verify(userService).getAllUsers();
    }

    @Test
    void getAllUsersShouldReturnEmptyList() throws Exception {
        
        when(userService.getAllUsers())
            .thenReturn(List.of());

        mockMvc.perform(get("/api/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        verify(userService).getAllUsers();
    }

}
