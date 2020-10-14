package com.upgrad.quora.service.business;

import com.upgrad.quora.service.dao.UserDao;
import com.upgrad.quora.service.entity.UserAuthEntity;
import com.upgrad.quora.service.entity.UsersEntity;
import com.upgrad.quora.service.exception.AuthenticationFailedException;
import com.upgrad.quora.service.exception.SignOutRestrictedException;
import com.upgrad.quora.service.exception.SignUpRestrictedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.UUID;

@Service
public class UserBusinessService {

    @Autowired
    private UserDao userDao;

    @Autowired
    private PasswordCryptographyProvider passwordCryptographyProvider;

    @Transactional(propagation = Propagation.REQUIRED)
    public UsersEntity signup(UsersEntity usersEntity) throws SignUpRestrictedException {

        UsersEntity userByUserName = userDao.getUser(usersEntity.getUserName());

        if (userByUserName != null) {
            throw new SignUpRestrictedException("SGR-002","This user has already been registered, try with any other emailId");
        }

        UsersEntity userByEmail = userDao.getUserByEmail(usersEntity.getEmail());
        if(userByEmail != null){
            throw new SignUpRestrictedException("SGR-001","Try any other Username, this Username has already been taken");
        }

        String[] encryptedText = passwordCryptographyProvider.encrypt(usersEntity.getPassword());
        usersEntity.setSalt(encryptedText[0]);
        usersEntity.setPassword(encryptedText[1]);
        return userDao.createUser(usersEntity);
    }





    @Transactional(propagation = Propagation.REQUIRED)
    public UserAuthEntity signin(final String username, final String password) throws AuthenticationFailedException {
       UsersEntity userEntity = userDao.getUser(username);
        if(userEntity == null){
            System.out.println(userEntity);
            throw new AuthenticationFailedException("ATH-001","This username does not exist");
        }

        final String encryptedPassword = passwordCryptographyProvider.encrypt(password,userEntity.getSalt());

        if(encryptedPassword.equals(userEntity.getPassword())){
            JwtTokenProvider jwtTokenProvider =new JwtTokenProvider(encryptedPassword);
            UserAuthEntity userAuthToken=new UserAuthEntity();
            userAuthToken.setUser(userEntity);
            userAuthToken.setUuid(UUID.randomUUID().toString());

            final ZonedDateTime now = ZonedDateTime.now();
            final ZonedDateTime expiresAt = now.plusHours(8);

            userAuthToken.setAccessToken(jwtTokenProvider.generateToken(userEntity.getUuid(),now,expiresAt));

            userAuthToken.setLoginAt(now);
            userAuthToken.setExpiresAt(expiresAt);

            userDao.createAuthToken(userAuthToken);
            userDao.updateUser(userEntity);
            userAuthToken.setLoginAt(now);
            return  userAuthToken;
        }
        else{
            throw new AuthenticationFailedException("ATH-002","Password failed");
        }
    }


    @Transactional(propagation = Propagation.REQUIRED)
    public UserAuthEntity signout(final String accessToken)throws SignOutRestrictedException {
        UserAuthEntity userByAccesstoken =userDao.getUserByAccessToken(accessToken);
        if(userByAccesstoken == null){
            throw  new SignOutRestrictedException("SGR-001","User is not Signed in");
        }
        final ZonedDateTime now = ZonedDateTime.now();
        userByAccesstoken.setLogoutAt(now);
        userDao.updateUserAuth(userByAccesstoken);
         return userByAccesstoken;
    }


}
