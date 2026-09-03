package com.minipay.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.minipay.common.exception.EmailAlreadyExistsException;
import com.minipay.common.exception.UserNotFoundException;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    @ValueSource(strings = { "", "  ", "     " })
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
            "ivan@example..com" })
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
    void createUserShouldReturnConflictWhenEmailAlreadyExists() throws Exception {

        when(userService.createUser("John Doe", "john.doe@example.com"))
                .thenThrow(new EmailAlreadyExistsException("john.doe@example.com"));

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "name": "John Doe",
                            "email": "john.doe@example.com"
                        }
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.path").value("/api/users"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.message")
                        .value("Email already exists: john.doe@example.com"));

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
    void getUserByIdShouldReturnNotFoundWhenUserNotFound() throws Exception {

        when(userService.getUserById(999L))
                .thenThrow(new UserNotFoundException(999L));

        mockMvc.perform(get("/api/users/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/users/999"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.message").value("User not found: 999"));

        verify(userService).getUserById(999L);
    }

    @Test
    void getUserByIdShouldReturnBadRequestWhenIdIsNotNumeric() throws Exception {
        mockMvc.perform(get("/api/users/{id}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request parameter"));

        verifyNoInteractions(userService);
    }

    @Test
    void getUserByIdShouldReturnInternalServerErrorWhenUnexpectedErrorOccurs() throws Exception {
        when(userService.getUserById(1L))
                .thenThrow(new RuntimeException("Internal diagnostic detail"));

        mockMvc.perform(get("/api/users/{id}", 1L))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Unexpected server error"))
                .andExpect(jsonPath("$.path").value("/api/users/1"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(userService).getUserById(1L);
    }

    @Test
    void getAllUsersShouldReturnUsers() throws Exception {
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
    void usersShouldReturnMethodNotAllowedForPut() throws Exception {
        mockMvc.perform(put("/api/users"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.message")
                        .value("HTTP method is not supported"));

        verifyNoInteractions(userService);
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

    @Test
    void createUserShouldReturnUnsupportedMediaTypeWhenContentTypeIsTextPlain() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.TEXT_PLAIN)
                .content("""
                        {
                            "name": "John Doe",
                            "email": "john.doe@example.com"
                        }
                        """))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string("Accept", containsString("application/json")))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"))
                .andExpect(jsonPath("$.path").value("/api/users"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.message").value("Content type is not supported"));

        verifyNoInteractions(userService);
    }

    @Test
    void unknownEndpointShouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/unknown-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/unknown-route"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.message").value("Endpoint not found"));

        verifyNoInteractions(userService);
    }

    @Test
    void createUserShouldReturnBadRequestWhenJsonIsMalformed() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/users"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.message")
                        .value("Invalid request body"));

        verifyNoInteractions(userService);
    }

}
