/*
 *
 *  * ******************************************************************************
 *  *  * Copyright (c) 2019 Skymind AI Bhd.
 *  *  * Copyright (c) 2020 CertifAI Sdn. Bhd.
 *  *  *
 *  *  * This program and the accompanying materials are made available under the
 *  *  * terms of the Apache License, Version 2.0 which is available at
 *  *  * https://www.apache.org/licenses/LICENSE-2.0.
 *  *  *
 *  *  * Unless required by applicable law or agreed to in writing, software
 *  *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  *  * License for the specific language governing permissions and limitations
 *  *  * under the License.
 *  *  *
 *  *  * SPDX-License-Identifier: Apache-2.0
 *  *  *****************************************************************************
 *
 *
 */

package global.skymind.solution.dataexamples;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.*;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

import javafx.stage.Stage;
import org.deeplearning4j.api.storage.StatsStorage;
import org.deeplearning4j.nn.conf.BackpropType;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.deeplearning4j.ui.api.UIServer;
import org.deeplearning4j.ui.stats.StatsListener;
import org.deeplearning4j.ui.storage.InMemoryStatsStorage;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Nesterovs;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.awt.*;
import java.util.Random;

/**
 * JavaFX application to show a neural network learning to draw an image.
 * Demonstrates how to feed an NN with externally originated data.
 *
 * This example uses JavaFX, which requires the Oracle JDK. Comment out this example if you use a different JDK.
 * OpenJDK and openjfx have been reported to work fine.
 *
 *
 * Program takes an image and regenerates it. Change originalImage to desired image path
 *
 * input - batchsize with each data input  (x,y ) output value (R,G,B);
 */

public class ImageDrawer extends Application {

    private Image originalImage; //The source image displayed on the left.
    private WritableImage composition; // Destination image generated by the NN.
    private MultiLayerNetwork model;

    private int width;
    private int height;

    private final int zoom = 1;// change to expand the view.
    private final int batchSize = 1000;
    private final int numBatches = 5;
    private final Random randGen = new Random();

    //x,y grid to calculate the output image. Calculated once, then re-used.
    //xyOut.size() = w * h, each input = (x_scaled, y_scaled)
    private INDArray xyOut;

    public static void main(String[] args) {
        launch(args);
    }

    //initialization before start()
    @Override
    public void init() throws Exception {
        originalImage = new Image("/DataExamples/Mona_Lisa.png"); // expecting a url

        width = (int) originalImage.getWidth();
        height = (int) originalImage.getHeight();
        composition = new WritableImage(width, height); //Right image.

        // The x,y grid scaling
        // to calculate the NN output only needs to be calculated once.
        int imageSize = width * height;
        xyOut = Nd4j.zeros(imageSize, 2);

        for (int i = 0; i < width; ++i) {
            double x_scaled = scaleXY(i, width);

            for (int j = 0; j < height; ++j) {
                int index = i + width * j;

                double y_scaled = scaleXY(j, height);

                //2 inputs. xScaled and yScaled.
                xyOut.put(index, 0, x_scaled);
                xyOut.put(index, 1, y_scaled);

            }
        }

        //init neural network
        model = createNN();

        // set to false if you do not want the web ui to track learning progress.
        boolean fUseUI = false;
        if (fUseUI) {
            UIServer uiServer = UIServer.getInstance();
            StatsStorage statsStorage = new InMemoryStatsStorage();
            uiServer.attach(statsStorage);
            model.setListeners(new StatsListener(statsStorage));
        }
        else
        {
            model.setListeners(new ScoreIterationListener(100));
        }

        drawImage();

    }

    /**
     * Make the Neural network draw the image.
     */
    private void drawImage()
    {
        //size of width * height, each contain RGB value
        INDArray outputRGB = model.output(xyOut);

        PixelWriter writer = composition.getPixelWriter();

        for(int x = 0; x < width; ++x)
        {
            for(int y = 0; y < height; ++y)
            {
                int index = x + width * y;

                double R = capNNOutput(outputRGB.getDouble(index, 0));
                double G = capNNOutput(outputRGB.getDouble(index, 1));
                double B = capNNOutput(outputRGB.getDouble(index, 2));

                Color pixel = new Color(R, G, B, 1.0);

                writer.setColor(x, y, pixel);
            }
        }


    }


    /**
     * Make sure the color values are >=0 and <=1
     */
    private static double capNNOutput(double x) {
        double tmp = (x < 0.0) ? 0.0 : x;
        return (tmp > 1.0) ? 1.0 : tmp;
    }

    /**
     * scale x,y points between min -0.5 and maximum 0.5++++
     */
    private static double scaleXY(int i, int maxI)
    {
        return (double) i / (double) (maxI - 1) -0.5;
    }

    /**
     * Standard JavaFX start: Build the UI, display
     */
    @Override
    public void start(Stage primaryStage)
    {
        ImageView iv1 = new ImageView(); //Left image
        iv1.setImage(originalImage);
        iv1.setFitHeight( zoom* height);
        iv1.setFitWidth( zoom * width);

        ImageView iv2 = new ImageView();
        iv2.setImage(composition);
        iv2.setFitHeight( zoom * height);
        iv2.setFitWidth(zoom * width);

        HBox root = new HBox(); //build the scene.
        Scene scene = new Scene(root);
        root.getChildren().addAll(iv1, iv2);

        primaryStage.setTitle("Generative Modelling");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(we -> System.exit(0));
        primaryStage.show();

        Platform.setImplicitExit(true);

        //Allow JavaFX do to it's thing, Initialize the Neural network when it feels like it.
        Platform.runLater(this::onCalc);
    }

    /**
     * Training the NN and updating the current graphical output.
     */
    private void onCalc()
    {
        for (int i = 0; i < numBatches; i++)
        {
            DataSet ds = generateDataSet();
            model.fit(ds);
        }

        drawImage();

        Platform.runLater(this::onCalc);

    }

    /**
     * Process a javafx Image to be consumed by DeepLearning4J.
     *
     * batchSize number of sample points to take out of the image.
     * @return DeepLearning4J DataSet.
     */
    private  DataSet generateDataSet()
    {
        PixelReader reader = originalImage.getPixelReader();

        INDArray xy = Nd4j.zeros(batchSize, 2);
        INDArray out = Nd4j.zeros(batchSize, 3);

        for (int k = 0; k < batchSize; ++k)
        {
            int w = randGen.nextInt(width);
            int h = randGen.nextInt(height);

            double xp = scaleXY(w, width);
            double yp = scaleXY(h, height);

            Color pixel = reader.getColor(w, h);

            xy.put(k, 0, xp); //2 inputs. x and y.
            xy.put(k, 1, yp);

            out.put(k, 0, pixel.getRed());  //3 outputs. the RGB values.
            out.put(k, 1, pixel.getGreen());
            out.put(k, 2, pixel.getBlue());
        }

        return new DataSet(xy, out);
    }



    /**
     * Build the generative neural network
     */
    private static MultiLayerNetwork createNN()
    {
        int seed = 123;
        double learningRate = 0.1;
        int inputNodes = 2; //x,y coordinate
        int hiddenNodes = 100;
        int outputNodes = 3; //RGB channels


        MultiLayerConfiguration config = new NeuralNetConfiguration.Builder()
                .seed(seed)
                .weightInit(WeightInit.XAVIER)
                .updater(new Nesterovs(learningRate, Nesterovs.DEFAULT_NESTEROV_MOMENTUM))
                //.optimizationAlgo()
                .list()
                .layer(0, new DenseLayer.Builder()
                        .nIn(inputNodes)
                        .nOut(hiddenNodes)
                        .activation(Activation.LEAKYRELU)
                        .build())
                .layer(1, new DenseLayer.Builder()
                        .nIn(hiddenNodes)
                        .nOut(hiddenNodes)
                        .activation(Activation.LEAKYRELU)
                        .build())
                .layer(2, new DenseLayer.Builder()
                        .nIn(hiddenNodes)
                        .nOut(hiddenNodes)
                        .activation(Activation.LEAKYRELU)
                        .build())
                .layer(3, new DenseLayer.Builder()
                        .nIn(hiddenNodes)
                        .nOut(hiddenNodes)
                        .activation(Activation.LEAKYRELU)
                        .build())
                .layer(4, new DenseLayer.Builder()
                        .nIn(hiddenNodes)
                        .nOut(hiddenNodes)
                        .activation(Activation.LEAKYRELU)
                        .build())
                .layer(5, new OutputLayer.Builder()
                        .nIn(hiddenNodes)
                        .nOut(outputNodes)
                        .activation(Activation.IDENTITY)
                        .lossFunction(LossFunctions.LossFunction.L2)
                        .build())
                .backpropType(BackpropType.Standard)
                .build();


        MultiLayerNetwork net = new MultiLayerNetwork(config);
        net.init();

        return net;

    }

}

