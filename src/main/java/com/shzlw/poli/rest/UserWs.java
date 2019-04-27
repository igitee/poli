package com.shzlw.poli.rest;

import com.shzlw.poli.dao.UserDao;
import com.shzlw.poli.filter.AuthFilter;
import com.shzlw.poli.model.User;
import com.shzlw.poli.service.UserService;
import com.shzlw.poli.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/ws/user")
public class UserWs {

    @Autowired
    UserDao userDao;

    @Autowired
    UserService userService;

    @RequestMapping(method = RequestMethod.GET)
    @Transactional(readOnly = true)
    public List<User> all(@CookieValue(Constants.SESSION_KEY) String sessionKey) {
        User myUser = userDao.findBySessionKey(sessionKey);
        List<User> users = new ArrayList<>();
        if (Constants.SYS_ROLE_ADMIN.equals(myUser.getSysRole())) {
            users = userDao.findNonAdminUsers(myUser.getId());
        } else if (Constants.SYS_ROLE_DEVELOPER.equals(myUser.getSysRole())) {
            users = userDao.findViewerUsers(myUser.getId());
        }

        // FIXME: N + 1 query
        for (User user : users) {
            List<Long> userGroups = userDao.findUserGroups(user.getId());
            user.setUserGroups(userGroups);
        }
        return users;
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    @Transactional(readOnly = true)
    public User one(@PathVariable("id") long userId) {
        User user = userDao.findById(userId);
        List<Long> userGroups = userDao.findUserGroups(userId);
        user.setUserGroups(userGroups);
        return user;
    }

    @RequestMapping(method = RequestMethod.POST)
    @Transactional
    public ResponseEntity<Long> add(@RequestBody User user) {
        long userId = userDao.insertUser(user.getUsername(), user.getName(), user.getTempPassword(), user.getSysRole());
        userDao.insertUserGroups(userId, user.getUserGroups());
        return new ResponseEntity<Long>(userId, HttpStatus.CREATED);
    }

    @RequestMapping(method = RequestMethod.PUT)
    @Transactional
    public ResponseEntity<?> update(@RequestBody User user) {
        long userId = user.getId();
        User savedUser = userDao.findById(userId);
        userService.removeFromSessionCache(savedUser.getSessionKey());

        userDao.updateUser(user);
        userDao.deleteUserGroups(userId);
        userDao.insertUserGroups(userId, user.getUserGroups());
        return new ResponseEntity<String>(HttpStatus.OK);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    @Transactional
    public ResponseEntity<?> delete(@PathVariable("id") long userId) {
        User savedUser = userDao.findById(userId);
        userService.removeFromSessionCache(savedUser.getSessionKey());

        userDao.deleteUserGroups(userId);
        userDao.deleteUser(userId);
        return new ResponseEntity<String>(HttpStatus.NO_CONTENT);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(UserWs.class);


    @RequestMapping(value = "/account", method = RequestMethod.GET)
    @Transactional(readOnly = true)
    public User findAccountBySessionKey(@CookieValue(Constants.SESSION_KEY) String sessionKey) {
        User myUser = userDao.findAccount(sessionKey);
        LOGGER.info("findAccountBySessionKey sessionKey: {}, myUser: {}", sessionKey, myUser);
        return myUser;
    }

    @RequestMapping(value = "/account", method = RequestMethod.PUT)
    @Transactional
    public void updateUserBySessionKey(@CookieValue(Constants.SESSION_KEY) String sessionKey, @RequestBody User user) {
        User myUser = userDao.findBySessionKey(sessionKey);
        userDao.updateUserAccount(myUser.getId(), user.getName(), user.getPassword());
    }
}