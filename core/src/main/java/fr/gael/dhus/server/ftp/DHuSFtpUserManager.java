/*
 * Data Hub Service (DHuS) - For Space data distribution.
 * Copyright (C) 2013,2014,2015 GAEL Systems
 *
 * This file is part of DHuS software sources.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * 
 */
package fr.gael.dhus.server.ftp;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ftpserver.ftplet.Authentication;
import org.apache.ftpserver.ftplet.AuthenticationFailedException;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.User;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.usermanager.AnonymousAuthentication;
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission;
import org.apache.ftpserver.usermanager.impl.TransferRatePermission;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.springframework.security.crypto.codec.Hex;

import fr.gael.dhus.database.dao.UserDao;
import fr.gael.dhus.database.object.User.PasswordEncryption;
import fr.gael.dhus.service.exception.UserBadEncryptionException;

/**
 * @author pidancier
 *
 */
public class DHuSFtpUserManager implements UserManager
{
   private static Log logger = LogFactory.getLog (DHuSFtpUserManager.class);
   private UserDao userDao;
   
   public DHuSFtpUserManager (UserDao userDao)
   {
      this.userDao = userDao; 
   }
   

   /* (non-Javadoc)
    * @see org.apache.ftpserver.ftplet.UserManager#authenticate(org.apache.ftpserver.ftplet.Authentication)
    */
   @Override
   public User authenticate(final Authentication authentication)
         throws AuthenticationFailedException
   {
      if (authentication instanceof UsernamePasswordAuthentication)
      {
         UsernamePasswordAuthentication upauth = 
               (UsernamePasswordAuthentication) authentication;

         String username = upauth.getUsername();
         String password = upauth.getPassword();
         User user = null;
         
         logger.debug("Trying to log as " + username);
         
         try
         {
            user = getUserByName (username);
                        
            fr.gael.dhus.database.object.User u = userDao.getByName(username);
            
            PasswordEncryption encryption = u.getPasswordEncryption ();
            if (encryption != PasswordEncryption.NONE) // when configurable
            {
               try
               {
                  MessageDigest md = MessageDigest.getInstance(encryption.getAlgorithmKey());
                  password  = new String(Hex.encode(md.digest(password.getBytes("UTF-8"))));
               }
               catch (Exception e)
               {
                  throw new UserBadEncryptionException ("There was an error while encrypting password.", e);
               }
            }
         }
         catch (FtpException e)
         {
            throw new AuthenticationFailedException("Authentication failed", e);
         }
         
         // Something missing in username
         if ((user == null) || (username == null))
         {
            throw new AuthenticationFailedException("Authentication failed (user not correct)");
         }
         // User not enable
         if (!user.getEnabled())
            throw new AuthenticationFailedException("User disabled.");
         
         // Simple auth with db infos (no pwd = free)
         if ((user.getPassword() == null)   ||
             (user.getPassword().equals("")) ||
             (user.getPassword().equals(password)))
         {
            return user;
         }
         throw new AuthenticationFailedException(
               "Authentication failed (wrong password)");
         
      }
      else if (authentication instanceof AnonymousAuthentication)
      {
         throw new AuthenticationFailedException(
            "Anonymous Authentication not authorized");
      }
      else
      {
         throw new IllegalArgumentException(
            "Authentication not supported by this user manager");
      }
   }

   /* (non-Javadoc)
    * @see org.apache.ftpserver.ftplet.UserManager#delete(java.lang.String)
    */
   @Override
   public void delete(String name) throws FtpException
   {
      throw new UnsupportedOperationException("Cannot delete User \"" + name +
            "\".");
   }

   /* (non-Javadoc)
    * @see org.apache.ftpserver.ftplet.UserManager#doesExist(java.lang.String)
    */
   @Override
   public boolean doesExist(String name)
   {
      return userDao.getByName(name) == null ? false: true;
   }

   /* (non-Javadoc)
    * @see org.apache.ftpserver.ftplet.UserManager#getAdminName()
    */
   @Override
   public String getAdminName() throws FtpException
   {
      return userDao.getRootUser().getUsername();
   }

   /* (non-Javadoc)
    * @see org.apache.ftpserver.ftplet.UserManager#getAllUserNames()
    */
   @Override
   public String[] getAllUserNames() throws FtpException
   {
      List<fr.gael.dhus.database.object.User>users = userDao.readNotDeleted ();
      List<String>names = new ArrayList<String>();
      for (fr.gael.dhus.database.object.User u:users)
         names.add(u.getUsername());
      return names.toArray(new String[names.size()]);
   }

   /* (non-Javadoc)
    * @see org.apache.ftpserver.ftplet.UserManager#getUserByName(java.lang.String)
    */
   @Override
   public User getUserByName(final String name) throws FtpException
   {
      if (name == null) return null;
      fr.gael.dhus.database.object.User u = userDao.getByName(name);
      
      if (u==null) return null;
      
      BaseUser user = new BaseUser();
      user.setName(u.getUsername());
      user.setPassword(u.getPassword());
      
      user.setEnabled(
         u.isEnabled()               && 
         u.isAccountNonExpired()     &&
         u.isAccountNonLocked()      &&
         u.isCredentialsNonExpired() &&
         !u.isDeleted());
      
      user.setHomeDirectory("/");
      List<Authority> authorities = new ArrayList<Authority>();
      authorities.add(new WritePermission ());
      // No special limit
      int maxLogin = 0;
      int maxLoginPerIP = 0;
      authorities.add(new ConcurrentLoginPermission(maxLogin, maxLoginPerIP));
      int uploadRate = 0;
      int downloadRate = 0;
      authorities.add(new TransferRatePermission(downloadRate, uploadRate));
      user.setAuthorities(authorities);
      user.setMaxIdleTime(1000);
      return user;
   }

   /* (non-Javadoc)
    * @see org.apache.ftpserver.ftplet.UserManager#isAdmin(java.lang.String)
    */
   @Override
   public boolean isAdmin(String s) throws FtpException
   {
      return getAdminName().equals(s);
   }

   /* (non-Javadoc)
    * @see org.apache.ftpserver.ftplet.UserManager#save(org.apache.ftpserver.ftplet.User)
    */
   @Override
   public void save(User user) throws FtpException
   {
      throw new FtpException ("Not allowed to save user by ftp.");
   }

}
