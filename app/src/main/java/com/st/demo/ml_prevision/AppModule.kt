package com.st.demo.ml_prevision

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun provideModelPrevision(@ApplicationContext context: Context): ModelPrevision {
        return ModelPrevision(context)
    }
}