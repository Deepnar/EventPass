package com.eventpass.eventpass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.eventpass.eventpass.entity.Organizer;
import com.eventpass.eventpass.service.OrganizerService;
import com.eventpass.eventpass.util.PasswordUtil;

@SpringBootTest
class CrudAndAuthTest {

    @Autowired OrganizerService organizerService;

    @Test
    void organizerCrudWithServerSideAuth() {
        Organizer o = organizerService.create("Jane", "jane@example.com", "Jane Events", "code1234");
        assertTrue(o.getId() != null);
        assertEquals("jane@example.com", organizerService.getById(o.getId()).getEmail());

        // wrong code rejected
        assertThrows(RuntimeException.class, () -> organizerService.authenticate(o.getId(), "wrong"));
        // right code works
        organizerService.authenticate(o.getId(), "code1234");

        Organizer updated = organizerService.update(o.getId(), "Jane Updated", null, "code1234");
        assertEquals("Jane Updated", updated.getName());

        // secrets are hashed, never plaintext
        assertTrue(!"code1234".equals(updated.getAccessCodeHash()));
        assertTrue(PasswordUtil.verify("code1234", updated.getAccessCodeHash()));

        organizerService.delete(o.getId(), "code1234");
        assertThrows(RuntimeException.class, () -> organizerService.getById(o.getId()));
    }

    @Test
    void duplicateOrganizerEmailRejected() {
        organizerService.create("A", "dup@example.com", "Org A", "code1234");
        assertThrows(RuntimeException.class,
                () -> organizerService.create("B", "dup@example.com", "Org B", "code1234"));
    }
}
