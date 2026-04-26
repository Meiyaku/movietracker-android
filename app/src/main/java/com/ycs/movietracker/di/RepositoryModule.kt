package com.ycs.movietracker.di

import com.ycs.movietracker.data.repository.AuthRepository
import com.ycs.movietracker.data.repository.FirebaseAuthRepository
import com.ycs.movietracker.data.repository.FirebaseMovieListRepository
import com.ycs.movietracker.data.repository.FirebaseMovieRepository
import com.ycs.movietracker.data.repository.FirebaseRemoteConfigRepository
import com.ycs.movietracker.data.repository.MovieListRepository
import com.ycs.movietracker.data.repository.MovieRepository
import com.ycs.movietracker.data.repository.RemoteConfigRepository
import com.ycs.movietracker.data.repository.TmdbRepository
import com.ycs.movietracker.data.repository.TmdbRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindMovieRepository(impl: FirebaseMovieRepository): MovieRepository

    @Binds
    @Singleton
    abstract fun bindMovieListRepository(impl: FirebaseMovieListRepository): MovieListRepository

    @Binds
    @Singleton
    abstract fun bindRemoteConfigRepository(impl: FirebaseRemoteConfigRepository): RemoteConfigRepository

    @Binds
    @Singleton
    abstract fun bindTmdbRepository(impl: TmdbRepositoryImpl): TmdbRepository
}
